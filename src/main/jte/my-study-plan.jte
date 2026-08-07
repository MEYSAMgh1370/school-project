@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(
    title = "برنامه‌ی مطالعه‌ی من",
    styles = java.util.List.of("/css/my-study-plan.css"),
    scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/modal-focus.js", "/js/my-study-plan.js"),
    content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" id="main-content" role="main" tabindex="-1"
         x-data="myStudyPlanPage()" @keydown.escape.window="pickerOpen = false; closeDialog()">
    <div class="container">
        <h1 class="title is-4">برنامه‌ی مطالعه‌ی من</h1>

        <div class="notification is-warning" x-show="!schoolId">
            برای مشاهده‌ی برنامه‌ی مطالعه، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>
                <div class="notification is-success" x-show="notice" x-text="notice"></div>

                    <div class="tabs">
                        <ul>
                            <li :class="{ 'is-active': tab === 'grid' }"><a href="#" @click.prevent="tab = 'grid'">برنامه هفتگی</a></li>
                            <li :class="{ 'is-active': tab === 'stats' }"><a href="#" @click.prevent="tab = 'stats'">آمار و گزارش</a></li>
                        </ul>
                    </div>

                <p x-show="loading">در حال بارگذاری...</p>

                <%-- ---------------------------------------------------- تب برنامه --%>
                <template x-if="tab === 'grid' && !loading">
                    <div>
                        <div class="sp-toolbar">
                            <div class="sp-weeknav">
                                <button class="button" type="button" @click="goPrevWeek" aria-label="هفته قبل">›</button>
                                <div class="sp-picker">
                                    <button class="button" type="button" @click="togglePicker" x-text="weekRangeLabel"></button>
                                    <template x-if="pickerOpen">
                                        <div class="sp-picker-pop" @click.outside="pickerOpen = false">
                                            <div class="sp-picker-head">
                                                <button class="button is-small" type="button" @click="pickerPrevMonth" aria-label="ماه قبل">›</button>
                                                <span class="has-text-weight-bold" x-text="pickerMonthLabel"></span>
                                                <button class="button is-small" type="button" @click="pickerNextMonth" aria-label="ماه بعد">‹</button>
                                            </div>
                                            <div class="sp-picker-grid">
                                                <template x-for="w in Jalali.weekdayShort" :key="w">
                                                    <div class="sp-picker-weekday" x-text="w"></div>
                                                </template>
                                            </div>
                                            <div class="sp-picker-grid">
                                                <template x-for="cell in pickerCells" :key="cell.key">
                                                    <div>
                                                        <button type="button" class="sp-picker-day"
                                                                :class="{ 'sp-picker-day--in-week': cell.inWeek, 'sp-picker-day--today': cell.isToday }"
                                                                x-show="!cell.blank" x-text="cell.label"
                                                                @click="pickDay(cell)"></button>
                                                    </div>
                                                </template>
                                            </div>
                                        </div>
                                    </template>
                                </div>
                                <button class="button" type="button" @click="goNextWeek" aria-label="هفته بعد">‹</button>
                                <button class="button is-light" type="button" x-show="!isCurrentWeek" @click="goThisWeek">این هفته</button>
                            </div>
                        </div>

                        <div class="sp-legend">
                            <span class="sp-legend-item"><span class="sp-swatch sp-swatch--pending"></span> در انتظار ثبت</span>
                            <span class="sp-legend-item"><span class="sp-swatch sp-swatch--done"></span> مطالعه انجام‌شده</span>
                        </div>

                        <div class="notification" x-show="totalIntervals === 0">
                            برای این هفته هنوز جلسه‌ی مطالعه‌ای برنامه‌ریزی نشده است. وقتی مشاور زمان‌های آزادِ شما را تنظیم کند، جلسه‌ها اینجا نمایش داده می‌شوند.
                        </div>

                        <div class="box sp-grid-box" x-show="totalIntervals > 0">
                            <div class="sp-grid">
                                <div class="sp-axis">
                                    <template x-for="h in hourMarks" :key="h">
                                        <div class="sp-hour" :style="'top:' + hourMarkTop(h) + 'px'" x-text="hourMarkLabel(h)"></div>
                                    </template>
                                </div>
                                <template x-for="day in weekDays" :key="day.iso">
                                    <div class="sp-day" :class="{ 'sp-day--today': day.isToday }">
                                        <div class="sp-day-head">
                                            <div class="sp-day-name" x-text="day.name"></div>
                                            <div :class="day.isToday ? 'sp-day-num sp-day-num--today' : 'sp-day-num'" x-text="day.dateNum"></div>
                                        </div>
                                        <div class="sp-day-body">
                                            <template x-for="block in day.blocks" :key="block.id">
                                                <button type="button" class="sp-block"
                                                        :class="block.done ? 'sp-block--done' : 'sp-block--pending'"
                                                        :style="'top:' + block.top + 'px; height:' + block.height + 'px'"
                                                        @click="openLog(block)">
                                                    <span class="sp-block-title" x-text="block.topicName"></span>
                                                    <span class="sp-block-time" x-show="block.showTime" x-text="block.timeLabel"></span>
                                                    <span class="sp-block-check" x-show="block.done">✓</span>
                                                </button>
                                            </template>
                                        </div>
                                    </div>
                                </template>
                            </div>
                        </div>
                    </div>
                </template>

                <%-- ------------------------------------------------------ تب آمار --%>
                <template x-if="tab === 'stats' && !loading">
                    <div>
                        <p class="has-text-grey mb-4" x-text="'آمار هفته‌ی ' + weekRangeLabel"></p>

                        <div class="sp-kpis">
                            <template x-for="kpi in kpis" :key="kpi.label">
                                <div class="sp-kpi">
                                    <div class="sp-kpi-label" x-text="kpi.label"></div>
                                    <div class="sp-kpi-value" x-text="kpi.value"></div>
                                </div>
                            </template>
                        </div>

                        <div class="sp-charts">
                            <div class="box sp-chart-card">
                                <p class="title is-6">ساعات مطالعه در هر روز</p>
                                <p class="is-size-7 has-text-grey">ستون‌ها ساعات برنامه‌ریزی‌شده‌اند؛ بخش پررنگ سهم انجام‌شده است.</p>
                                <div class="sp-daybars">
                                    <template x-for="bar in dayBars" :key="bar.name">
                                        <div class="sp-daybar-col">
                                            <span class="is-size-7 has-text-grey" x-text="bar.valueLabel"></span>
                                            <div class="sp-daybar-track" :style="'height:' + bar.trackHeight + 'px'">
                                                <div class="sp-daybar-fill" :style="'height:' + bar.fillPct + '%'"></div>
                                            </div>
                                            <span class="is-size-7" x-text="bar.name"></span>
                                        </div>
                                    </template>
                                </div>
                            </div>

                            <div class="box sp-chart-card">
                                <p class="title is-6">ساعات هر سرفصل</p>
                                <p class="is-size-7 has-text-grey">تعداد جلسه‌های انجام‌شده از کل جلسه‌های همان سرفصل.</p>
                                <div class="mt-4">
                                    <template x-for="tbar in topicBars" :key="tbar.topicName">
                                        <div class="sp-topicbar">
                                            <div class="sp-topicbar-head">
                                                <span class="has-text-weight-semibold" x-text="tbar.topicName"></span>
                                                <span class="has-text-grey" x-text="tbar.valueLabel"></span>
                                            </div>
                                            <div class="sp-topicbar-track">
                                                <div class="sp-topicbar-fill" :style="'width:' + tbar.fillPct + '%'"></div>
                                            </div>
                                        </div>
                                    </template>
                                    <p class="has-text-grey" x-show="topicBars.length === 0">هنوز داده‌ای برای این هفته نیست.</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </template>
            </div>
        </template>
    </div>

    <%-- ---------------------------------------------------------- گفتگوی ثبت --%>
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="studyDialogTitle"
         tabindex="-1" x-ref="studyDialog" :class="{ 'is-active': dialog }" x-cloak
         @keydown.tab="window.ModalFocus.trap($event, $el)" @keydown.escape.stop="closeDialog">
        <div class="modal-background" @click="closeDialog"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title" id="studyDialogTitle" x-text="dialog ? dialog.topicName : ''"></p>
                <button class="delete" type="button" aria-label="بستن" @click="closeDialog"></button>
            </header>
            <section class="modal-card-body">
                <p class="has-text-grey mb-4" x-text="dialog ? dialog.timeLabel : ''"></p>
                    <label class="checkbox mb-4 app-block">
                    <input type="checkbox" x-ref="dialogFirstControl" x-model="dialogDone">
                    این جلسه را کامل مطالعه کردم
                </label>
                <div class="field">
                    <label class="label" for="dialogTestCount">تعداد تست زده‌شده</label>
                    <div class="control">
                        <input class="input" id="dialogTestCount" type="number" min="0" x-model.number="dialogTestCount">
                    </div>
                </div>
            </section>
            <footer class="modal-card-foot">
                <button class="button is-link" type="button" :class="{ 'is-loading': saving }" @click="saveDialog">ذخیره</button>
                <button class="button" type="button" @click="closeDialog">انصراف</button>
            </footer>
        </div>
    </div>
</section>
`)

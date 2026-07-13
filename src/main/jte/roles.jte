@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(title = "نقش‌ها و دسترسی‌ها", scripts = java.util.List.of("/js/navbar.js", "/js/roles.js"), content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" x-data="rolesPage()">
    <div class="container">
        <h1 class="title is-4">نقش‌ها و دسترسی‌ها</h1>
        <p class="subtitle is-6">نقش‌های سیستمی ثابت‌اند؛ می‌توانید نقش سفارشیِ تازه با ترکیبی از Permissionهای موجود بسازید.</p>

        <div class="notification is-warning" x-show="!schoolId">
            برای مدیریت نقش‌ها، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>
                <div class="notification is-success" x-show="notice" x-text="notice"></div>

                <template x-if="!blocked">
                    <div>
                        <div class="box">
                            <h2 class="title is-6">ساخت نقش سفارشی</h2>
                            <form @submit.prevent="createRole">
                                <div class="field is-grouped is-grouped-multiline">
                                    <div class="control">
                                        <label class="label" for="roleCode">کد نقش (لاتین، مثل LIBRARIAN)</label>
                                        <input class="input" dir="ltr" id="roleCode" type="text" x-model="form.code" required>
                                    </div>
                                    <div class="control is-expanded">
                                        <label class="label" for="roleDisplayName">عنوان فارسی</label>
                                        <input class="input" id="roleDisplayName" type="text" x-model="form.displayName" required>
                                    </div>
                                </div>
                                <div class="field">
                                    <label class="label">Permissionها</label>
                                    <div class="control">
                                        <template x-for="permission in permissions" :key="permission.code">
                                            <label class="checkbox mr-4">
                                                <input type="checkbox" :value="permission.code" x-model="form.permissionCodes">
                                                <span x-text="permission.displayName"></span>
                                            </label>
                                        </template>
                                    </div>
                                </div>
                                <button class="button is-link" type="submit" :class="{ 'is-loading': submitting }">
                                    ساخت نقش
                                </button>
                            </form>
                        </div>

                        <h2 class="title is-6">نقش‌های این مدرسه</h2>
                        <table class="table is-fullwidth is-striped">
                            <thead>
                            <tr>
                                <th>کد</th>
                                <th>عنوان</th>
                                <th>نوع</th>
                                <th>Permissionها</th>
                            </tr>
                            </thead>
                            <tbody>
                            <template x-for="role in roles" :key="role.id">
                                <tr>
                                    <td dir="ltr" x-text="role.code"></td>
                                    <td x-text="role.displayName"></td>
                                    <td>
                                        <span class="tag" :class="role.custom ? 'is-info' : ''"
                                              x-text="role.custom ? 'سفارشی' : 'سیستمی'"></span>
                                    </td>
                                    <td>
                                        <template x-for="code in role.permissionCodes" :key="code">
                                            <span class="tag mr-1 mb-1" x-text="permissionLabel(code)"></span>
                                        </template>
                                    </td>
                                </tr>
                            </template>
                            </tbody>
                        </table>
                    </div>
                </template>
            </div>
        </template>
    </div>
</section>
`)

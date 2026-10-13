package com.example.roshdeandishe;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** هر SQLِ Hibernate را برای تست‌هایی که شکلِ پرس‌وجوها را بررسی می‌کنند نگه می‌دارد. */
public class SqlCapture implements StatementInspector {

    static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }
}

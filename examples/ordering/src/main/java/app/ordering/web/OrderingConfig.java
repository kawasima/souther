package app.ordering.web;

import app.ordering.JooqAllocateStock;
import app.ordering.JooqRecordOrder;

import example.ordering.在庫を引き当てる;
import example.ordering.注文;
import example.ordering.注文を処理する;
import example.ordering.注文を処理する結果;
import example.ordering.注文を記録する;

import net.unit8.souther.runtime.Behavior;

import org.jooq.DSLContext;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Souther 生成物と Spring の配線。DataSource / DSLContext / TransactionManager / schema.sql 実行は
 * すべて Spring Boot の autoconfig（{@code spring-boot-starter-jooq} + H2）に任せる。ここで足すのは
 * 生成物側の Bean（注入実装と束縛済みパイプライン）と、プログラマティックなトランザクション境界の
 * 道具（{@link TransactionTemplate}）だけ。
 *
 * <p>autoconfig の DSLContext は {@code TransactionAwareDataSourceProxy} 経由なので、jOOQ の各文は
 * Spring がスレッドに束縛したトランザクションのコネクションを使う。だから前段 INSERT と後段 UPDATE が
 * 同一トランザクションに入り、まとめて巻き戻せる。
 */
@Configuration(proxyBeanMethods = false)
public class OrderingConfig {

    /**
     * jOOQ の識別子 quote を止める Bean。JooqAutoConfiguration がこの Settings を拾う。未quote 名は
     * H2 が大文字に畳むので、コード側の小文字名 orders / stock / qty がスキーマの ORDERS / STOCK / QTY に一致する。
     */
    @Bean
    public Settings jooqSettings() {
        return new Settings().withRenderQuotedNames(RenderQuotedNames.NEVER);
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }

    // --- 注入する外界依存の実装（DSLContext は autoconfig から注入される） ---
    @Bean
    public 注文を記録する 注文を記録する(DSLContext dsl) {
        return new JooqRecordOrder(dsl);
    }

    @Bean
    public 在庫を引き当てる 在庫を引き当てる(DSLContext dsl) {
        return new JooqAllocateStock(dsl);
    }

    /**
     * 束縛済みパイプライン。要求集合 {@code {注文を記録する, 在庫を引き当てる}} はコンパイラが推論し、
     * それを満たす実装を bind で注入する（spec 19.5）。
     */
    @Bean
    public Behavior<注文, 注文を処理する結果> 注文を処理する(
            注文を記録する 記録, 在庫を引き当てる 引当) {
        return 注文を処理する.bind(記録, 引当);
    }
}

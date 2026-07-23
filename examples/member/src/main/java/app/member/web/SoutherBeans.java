package app.member.web;

import app.member.JooqFindMember;
import app.member.Loggingメール送信;
import example.member.会員へ通知する;
import example.member.会員を照会し整形する;

import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Souther 生成物と Spring の配線。required behavior を Java 実装で束縛し、
 * 束縛済みパイプラインを Bean にする（spec 19.5 の {@code bind}）。
 */
@Configuration
public class SoutherBeans {

    /**
     * {@code findMember} を jOOQ 実装で束縛したパイプライン。
     * 要求集合 {@code {findMember}} はコンパイラが推論しており、それを満たす実装を注入する。
     */
    @Bean
    public 会員を照会し整形する 会員照会パイプライン(DSLContext dsl) {
        return 会員を照会し整形する.bind(new JooqFindMember(dsl));
    }

    /**
     * {@code 会員へ通知する} を通知メール送信のダミー実装（ログ出力）で束縛したパイプライン。
     * 要求集合 {@code {通知メールを送る}} を満たす。宛先の型が {@code アクティベート済み} なので、
     * 未アクティベートのアドレスには通知を送れない（型で保証）。
     */
    @Bean
    public 会員へ通知する 会員通知パイプライン() {
        return 会員へ通知する.bind(new Loggingメール送信());
    }
}

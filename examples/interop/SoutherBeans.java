package example.member.web;

import example.member.JooqFindMember;
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
}

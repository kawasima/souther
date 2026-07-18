package app.ordering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot の起動点。{@code @SpringBootTest} はこのクラス（{@code @SpringBootConfiguration}）を
 * 見つけてコンテキストを起動する。infra Bean（DataSource / DSLContext / TransactionManager）は
 * {@link app.ordering.web.OrderingConfig} で明示的に定義する ── offline に spring-boot-jooq /
 * sql-init が無いので autoconfig には頼らない。トランザクション制御そのものが主題なので、
 * TransactionManager が何かを隠さず見せる意図でもある。
 *
 * <p>{@code proxyBeanMethods = false} で @Configuration の CGLIB プロキシを外す（生成物は Java 25
 * バイトコード。プロキシ生成の副作用を避ける）。
 */
@SpringBootApplication(proxyBeanMethods = false)
public class OrderingApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderingApplication.class, args);
    }
}

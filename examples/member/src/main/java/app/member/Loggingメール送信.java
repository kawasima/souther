// 注入 behavior 通知メールを送る のダミー実装。生成された抽象基底 通知メールを送る
// （Behavior<通知, 送信済み>）を extends し、実際の送信の代わりにログを出す。
// 宛先の型は アクティベート済み なので、未アクティベートのアドレスはそもそもここへ渡ってこない
// ——「アクティベートでないと通知が送れない」を型で保証したうえでのダミー。
package app.member;

import example.member.通知;
import example.member.通知メールを送る;
import example.member.送信済み;
import example.member.メールアドレス;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

public final class Loggingメール送信 extends 通知メールを送る {

    private static final Logger LOG = System.getLogger(Loggingメール送信.class.getName());

    @Override
    public 送信済み apply(通知 通知) {
        メールアドレス 宛先 = 通知.宛先().value();
        LOG.log(Level.INFO, "通知メール送信（ダミー）: 宛先={0} 件名={1}", 宛先.value(), 通知.件名());
        return 送信済み(宛先);   // 基底から継承した protected ファクトリ（constructs 送信済み）
    }
}

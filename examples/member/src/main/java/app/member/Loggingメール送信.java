// 注入 behavior 通知メールを送る のダミー実装。生成された抽象基底 通知メールを送る
// （複数入力なので Behavior は継がない standalone 抽象クラス・apply(アクティベート済み, String)）を
// extends し、実際の送信の代わりにログを出す。宛先の型は アクティベート済み なので、未アクティベートの
// アドレスはそもそもここへ渡ってこない——「アクティベートでないと通知が送れない」を型で保証したうえでのダミー。
package app.member;

import example.member.アクティベート済み;
import example.member.通知メールを送る;
import example.member.送信済み;
import example.member.メールアドレス;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

public final class Loggingメール送信 extends 通知メールを送る {

    private static final Logger LOG = System.getLogger(Loggingメール送信.class.getName());

    @Override
    public 送信済み apply(アクティベート済み 宛先, String 件名) {
        メールアドレス アドレス = 宛先.value();
        LOG.log(Level.INFO, "通知メール送信（ダミー）: 宛先={0} 件名={1}", アドレス.value(), 件名);
        return 送信済み(アドレス);   // 基底から継承した protected ファクトリ（constructs 送信済み）
    }
}

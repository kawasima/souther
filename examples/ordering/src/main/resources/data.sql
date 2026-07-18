-- 商品 ITEM-A の初期在庫 10。テストは @BeforeEach でこの状態に戻す。
MERGE INTO stock (item_id, qty) KEY(item_id) VALUES ('ITEM-A', 10);

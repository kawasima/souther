-- Initial stock per sku. The tests reset to this state in @BeforeEach.
-- apple has plenty; tv is scarce, so a cart that orders more than 3 tvs is out of stock.
MERGE INTO stock (sku, qty) KEY(sku) VALUES ('apple', 10);
MERGE INTO stock (sku, qty) KEY(sku) VALUES ('tv', 3);

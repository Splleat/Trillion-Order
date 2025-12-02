INSERT INTO "delivery_policy" ("deliverypolicy_fee", "deliverypolicy_threshold") VALUES (5000, 30000);
INSERT INTO "packaging" ("packaging_type", "packaging_price") VALUES ('WOOD', 500);
INSERT INTO "packaging" ("packaging_type", "packaging_price") VALUES ('STONE', 1000);
INSERT INTO "packaging" ("packaging_type", "packaging_price") VALUES ('IRON', 2000);



INSERT INTO "order" (
    order_number,
    order_status,
    member_id,
    total_price,
    delivery_fee,
    point_usage,
    coupon_id,
    orderer_name,
    orderer_contact,
    receiver_name,
    receiver_contact,
    receiver_address) VALUES (
             'ORD-TEST-001',
             'PENDING',
             1,
             50000,
             3000,
             0,
             NULL,
             '홍길동',
             '010-1234-5678',
             '김철수',
             '010-9876-5432',
             '광주광역시 동구 서석동'
         );
INSERT INTO "order_item" (
    order_id,
    book_id,
    quantity,
    price,
    packaging_price,
    orderitem_status
) VALUES (
             (SELECT order_id FROM "Order" WHERE order_number = 'ORD-TEST-001'),
             1,
             2,
             25000,
             0,
             'PREPARING'
         );
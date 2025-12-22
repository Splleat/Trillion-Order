-- 배송 정책
INSERT INTO "delivery_policy" ("deliverypolicy_fee", "deliverypolicy_threshold") VALUES (5000, 30000);

-- 포장 정책
INSERT INTO "packaging" ("packaging_type", "packaging_price") VALUES ('WOOD', 500);
INSERT INTO "packaging" ("packaging_type", "packaging_price") VALUES ('STONE', 1000);
INSERT INTO "packaging" ("packaging_type", "packaging_price") VALUES ('IRON', 2000);

-- 샘플 주문 1: 회원 주문 - 완료된 상태 (ID: 101)
INSERT INTO "order" ("order_id", "order_number", "member_id", "non_member_password", "order_status",
                     "orderer_name", "orderer_contact",
                     "receiver_name", "receiver_contact", "receiver_address",
                     "order_date", "shipping_post_code", "delivery_date", "delivery_fee", "point_usage", "coupon_discount_amount",
                     "origin_price", "total_price")
VALUES (101, 'ORD-MEMBER-COMPLETED-001', 1, null, 'COMPLETED',
        '회원주문완료자', '010-1111-2222',
        '회원수령완료인', '010-3333-4444', '서울특별시 강남구 테헤란로 123',
        CURRENT_TIMESTAMP - INTERVAL '10' DAY, '06134', CURRENT_TIMESTAMP - INTERVAL '7' DAY, 0, 1000, 5000,
        61000, 55000);

-- 샘플 주문 1 상품
INSERT INTO "order_item" ("orderitem_id", "order_id", "book_id", "book_name", "book_image", "quantity", "price", "coupon_discount_amount", "shipping_date", "packaging_price", "orderitem_status")
VALUES (101, 101, 1, '자바의 정석', '/images/book1.jpg', 2, 25000, 5000, CURRENT_TIMESTAMP - INTERVAL '8' DAY, 500, 'CONFIRMED');

INSERT INTO "order_item" ("orderitem_id", "order_id", "book_id", "book_name", "book_image", "quantity", "price", "coupon_discount_amount", "shipping_date", "packaging_price", "orderitem_status")
VALUES (102, 101, 2, '클린 코드', '/images/book2.jpg', 1, 10000, 0, CURRENT_TIMESTAMP - INTERVAL '8' DAY, 0, 'CONFIRMED');

-- 샘플 주문 1에 사용된 쿠폰 정보
INSERT INTO "order_coupon" ("order_id", "coupon_id", "discount_amount", "target_id") VALUES (101, 1, 5000, null);


-- 샘플 주문 2: 회원 주문 - 취소된 상태 (ID: 102)
INSERT INTO "order" ("order_id", "order_number", "member_id", "non_member_password", "order_status",
                     "orderer_name", "orderer_contact",
                     "receiver_name", "receiver_contact", "receiver_address",
                     "order_date", "shipping_post_code", "delivery_date", "delivery_fee", "point_usage", "coupon_discount_amount",
                     "origin_price", "total_price")
VALUES (102, 'ORD-MEMBER-CANCELED-002', 1, null, 'CANCELED',
        '회원주문취소자', '010-5555-6666',
        '회원수령취소인', '010-7777-8888', '부산광역시 해운대구 센텀중앙로 10',
        CURRENT_TIMESTAMP - INTERVAL '5' DAY, '48058', CURRENT_TIMESTAMP + INTERVAL '2' DAY, 3000, 0, 0,
        31000, 34000);

-- 샘플 주문 2 상품
INSERT INTO "order_item" ("orderitem_id", "order_id", "book_id", "book_name", "book_image", "quantity", "price", "coupon_discount_amount", "shipping_date", "packaging_price", "orderitem_status")
VALUES (103, 102, 3, 'HTTP 완벽 가이드', '/images/book3.jpg', 1, 15000, 0, null, 1000, 'CANCELED');

INSERT INTO "order_item" ("orderitem_id", "order_id", "book_id", "book_name", "book_image", "quantity", "price", "coupon_discount_amount", "shipping_date", "packaging_price", "orderitem_status")
VALUES (104, 102, 4, '객체지향의 사실과 오해', '/images/book4.jpg', 1, 15000, 0, null, 0, 'CANCELED');

-- 샘플 주문 3: 비회원 주문 - 부분 반품 상태 (ID: 103)
INSERT INTO "order" ("order_id", "order_number", "member_id", "non_member_password", "order_status",
                     "orderer_name", "orderer_contact",
                     "receiver_name", "receiver_contact", "receiver_address",
                     "order_date", "shipping_post_code", "delivery_date", "delivery_fee", "point_usage", "coupon_discount_amount",
                     "origin_price", "total_price")
VALUES (103, 'ORD-GUEST-PARTIAL-003', null, '$2a$12$CHTyAGNLcQI9JdQHplZoCucsC1IgBdfnJPGg4R9WZ01sYOPrWYmR2', 'COMPLETED', -- 비밀번호: 1234
        '비회원주문자', '010-9999-0000',
        '비회원수령인', '010-1010-2020', '대전광역시 유성구 대학로 99',
        CURRENT_TIMESTAMP - INTERVAL '20' DAY, '34134', CURRENT_TIMESTAMP - INTERVAL '15' DAY, 0, 0, 0,
        51000, 51000);

-- 샘플 주문 3 상품
INSERT INTO "order_item" ("orderitem_id", "order_id", "book_id", "book_name", "book_image", "quantity", "price", "coupon_discount_amount", "shipping_date", "packaging_price", "orderitem_status")
VALUES (105, 103, 1, '자바의 정석', '/images/book1.jpg', 1, 20000, 0, CURRENT_TIMESTAMP - INTERVAL '18' DAY, 0, 'CONFIRMED');

INSERT INTO "order_item" ("orderitem_id", "order_id", "book_id", "book_name", "book_image", "quantity", "price", "coupon_discount_amount", "shipping_date", "packaging_price", "orderitem_status")
VALUES (106, 103, 2, '클린 코드', '/images/book2.jpg', 1, 30000, 0, CURRENT_TIMESTAMP - INTERVAL '18' DAY, 1000, 'RETURNED');
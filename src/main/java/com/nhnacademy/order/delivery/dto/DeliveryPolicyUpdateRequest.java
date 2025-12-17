/*
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 * + Copyright 2025. NHN Academy Corp. All rights reserved.
 * + * While every precaution has been taken in the preparation of this resource,  assumes no
 * + responsibility for errors or omissions, or for damages resulting from the use of the information
 * + contained herein
 * + No part of this resource may be reproduced, stored in a retrieval system, or transmitted, in any
 * + form or by any means, electronic, mechanical, photocopying, recording, or otherwise, without the
 * + prior written permission.
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 */

package com.nhnacademy.order.delivery.dto;

import jakarta.validation.constraints.Min;

public record DeliveryPolicyUpdateRequest(
    @Min(value = 0, message = "배송비는 0원 이상이어야 합니다.")
    int deliveryPolicyFee,
    @Min(value = 0, message = "무료 배송 기준액은 0원 이상이어야 합니다.")
    int deliveryPolicyThreshold
) {}

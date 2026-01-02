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

package com.nhnacademy.order.packaging.controller;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.packaging.dto.PackagingCreateRequest;
import com.nhnacademy.order.packaging.dto.PackagingResponse;
import com.nhnacademy.order.packaging.dto.PackagingUpdateRequest;
import com.nhnacademy.order.packaging.service.PackagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/orders/packaging")
public class PackagingControllerImpl implements PackagingController {
    private final PackagingService packagingService;

    @Override
    @GetMapping
    public ResponseEntity<List<PackagingResponse>> getAllPackaging() {
        List<PackagingResponse> response = packagingService.getAllPackaging();

        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping
    public ResponseEntity<PackagingResponse> createPackaging(@RequestBody @Valid PackagingCreateRequest request, UserInfo userInfo) {
        PackagingResponse response = packagingService.createPackaging(userInfo, request);

        return ResponseEntity.ok(response);
    }

    @Override
    @PutMapping("/{packaging-id}")
    public ResponseEntity<Void> updatePackaging(@PathVariable("packaging-id") Long packagingId,
                                                @RequestBody @Valid PackagingUpdateRequest request,
                                                UserInfo userInfo) {

        packagingService.updatePackaging(userInfo, packagingId, request);

        return ResponseEntity.ok().build();
    }

    @Override
    @DeleteMapping("/{packaging-id}")
    public ResponseEntity<Void> removePackaging(@PathVariable("packaging-id") Long packagingId,
                                                UserInfo userInfo) {

        packagingService.deletePackaging(userInfo, packagingId);

        return ResponseEntity.noContent().build();
    }
}

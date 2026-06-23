package com.zwinsight.purchase.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.purchase.domain.BizBidResult;
import com.zwinsight.purchase.service.BidRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 定标结果接口
 */
@RestController
@RequestMapping("/api/v1/purchase/bid")
@RequiredArgsConstructor
public class BidResultController {

    private final BidRankingService bidRankingService;

    @PostMapping("/calculate/{inquiryId}")
    public R<List<BizBidResult>> calculateRanking(@PathVariable Long inquiryId) {
        return R.ok(bidRankingService.calculateRanking(inquiryId));
    }

    @PostMapping("/confirm")
    public R<Void> confirmWinner(@RequestBody Map<String, Long> body) {
        Long inquiryId = body.get("inquiryId");
        Long supplierId = body.get("supplierId");
        bidRankingService.confirmWinner(inquiryId, supplierId);
        return R.ok();
    }

    @GetMapping("/{inquiryId}")
    public R<List<BizBidResult>> getByInquiry(@PathVariable Long inquiryId) {
        return R.ok(bidRankingService.getByInquiry(inquiryId));
    }
}

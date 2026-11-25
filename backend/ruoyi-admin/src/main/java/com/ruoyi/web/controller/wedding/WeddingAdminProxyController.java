package com.ruoyi.web.controller.wedding;

import java.util.LinkedHashMap;
import java.util.Map;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;

/**
 * 吾喜备婚运营管理代理控制器（支付订单管理 + 激活码管理 + 微信用户与婚礼空间管理）
 */
@RestController
@RequestMapping("/wedding/admin")
public class WeddingAdminProxyController extends BaseController
{
    private final WeddingAdminProxyService proxyService;

    public WeddingAdminProxyController(WeddingAdminProxyService proxyService)
    {
        this.proxyService = proxyService;
    }

    @PreAuthorize("@ss.hasPermi('wedding:order:list')")
    @GetMapping("/pay-orders")
    public AjaxResult listPayOrders(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String outTradeNo,
            @RequestParam(required = false) String openid,
            @RequestParam(required = false) Long weddingId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String envMode)
    {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("pageNum", pageNum);
        query.put("pageSize", pageSize);
        query.put("outTradeNo", outTradeNo);
        query.put("openid", openid);
        query.put("weddingId", weddingId);
        query.put("status", status);
        query.put("envMode", envMode);
        return proxyService.get("/api/admin/pay-orders", query);
    }

    @PreAuthorize("@ss.hasPermi('wedding:redeem:list')")
    @GetMapping("/redeem-codes")
    public AjaxResult listRedeemCodes(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Long weddingId)
    {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("pageNum", pageNum);
        query.put("pageSize", pageSize);
        query.put("batchNo", batchNo);
        query.put("status", status);
        query.put("code", code);
        query.put("weddingId", weddingId);
        return proxyService.get("/api/admin/redeem-codes", query);
    }

    @PreAuthorize("@ss.hasPermi('wedding:redeem:create')")
    @Log(title = "吾喜备婚-批量生成激活码", businessType = BusinessType.INSERT)
    @PostMapping("/redeem-codes/batch")
    public AjaxResult generateBatchCodes(@RequestBody Map<String, Object> body)
    {
        return proxyService.post("/api/admin/redeem-codes/batch", body);
    }

    @PreAuthorize("@ss.hasPermi('wedding:redeem:void')")
    @Log(title = "吾喜备婚-作废激活码", businessType = BusinessType.UPDATE)
    @PostMapping("/redeem-codes/{code}/void")
    public AjaxResult voidRedeemCode(@PathVariable("code") String code)
    {
        String safeCode = UriUtils.encodePathSegment(code, StandardCharsets.UTF_8);
        return proxyService.post("/api/admin/redeem-codes/" + safeCode + "/void", null);
    }

    @PreAuthorize("@ss.hasPermi('wedding:user:list')")
    @GetMapping("/wx-users")
    public AjaxResult listWxUsers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String openid,
            @RequestParam(required = false) Long weddingId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer isPro,
            @RequestParam(required = false) Integer status)
    {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("pageNum", pageNum);
        query.put("pageSize", pageSize);
        query.put("openid", openid);
        query.put("weddingId", weddingId);
        query.put("role", role);
        query.put("isPro", isPro);
        query.put("status", status);
        return proxyService.get("/api/admin/wx-users", query);
    }

    @PreAuthorize("@ss.hasPermi('wedding:user:edit')")
    @Log(title = "吾喜备婚-编辑微信用户", businessType = BusinessType.UPDATE)
    @PutMapping("/wx-users/{openid}")
    public AjaxResult updateWxUser(
            @PathVariable("openid") String openid,
            @RequestBody Map<String, Object> body)
    {
        String safeOpenid = UriUtils.encodePathSegment(openid, StandardCharsets.UTF_8);
        return proxyService.put("/api/admin/wx-users/" + safeOpenid, body);
    }

    @PreAuthorize("@ss.hasPermi('wedding:user:list')")
    @GetMapping("/weddings/{id}")
    public AjaxResult getWeddingDetail(@PathVariable("id") Long id)
    {
        return proxyService.get("/api/admin/weddings/" + id, null);
    }
}

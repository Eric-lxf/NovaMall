package com.ruoyi.web.controller.wedding;

import com.ruoyi.common.core.domain.AjaxResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WeddingAdminProxyServiceTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private WeddingAdminProperties properties;
    private WeddingAdminProxyService proxyService;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        properties = new WeddingAdminProperties();
        properties.setBaseUrl("http://127.0.0.1:8081");
        properties.setToken("shared_admin_secret_2026");
        proxyService = new WeddingAdminProxyService(properties, restTemplate);
    }

    @Test
    void returnsErrorWhenAdminTokenIsMissing() {
        properties.setToken("");
        AjaxResult res = proxyService.get("/api/admin/redeem-codes", Map.of("pageNum", 1));
        assertEquals(500, res.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(res.get(AjaxResult.MSG_TAG)).contains("WEDDING_ADMIN_TOKEN"));
    }

    @Test
    void forwardsGetWithTokenAndMapsApiResponseToAjaxResult() {
        mockServer.expect(requestTo("http://127.0.0.1:8081/api/admin/redeem-codes?pageNum=1&batchNo=XHS_01"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Admin-Token", "shared_admin_secret_2026"))
                .andRespond(withSuccess(
                        "{\"code\":200,\"message\":\"OK\",\"data\":{\"total\":10,\"rows\":[]}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> query = new java.util.LinkedHashMap<>();
        query.put("pageNum", 1);
        query.put("batchNo", "XHS_01");
        query.put("status", null);

        AjaxResult res = proxyService.get("/api/admin/redeem-codes", query);
        mockServer.verify();
        assertEquals(200, res.get(AjaxResult.CODE_TAG));
        assertEquals("OK", res.get(AjaxResult.MSG_TAG));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) res.get(AjaxResult.DATA_TAG);
        assertEquals(10, data.get("total"));
    }

    @Test
    void mapsUpstream400ErrorMessageToAjaxResultMsg() {
        mockServer.expect(requestTo("http://127.0.0.1:8081/api/admin/redeem-codes/W8K9P2/void"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Admin-Token", "shared_admin_secret_2026"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":400,\"message\":\"该激活码已核销使用，不可直接作废\",\"data\":null}"));

        AjaxResult res = proxyService.post("/api/admin/redeem-codes/W8K9P2/void", null);
        mockServer.verify();
        assertEquals(400, res.get(AjaxResult.CODE_TAG));
        assertEquals("该激活码已核销使用，不可直接作废", res.get(AjaxResult.MSG_TAG));
    }

    @Test
    void returnsFriendly503WhenWeddingBackendIsUnreachable() {
        mockServer.expect(requestTo("http://127.0.0.1:8081/api/admin/wx-users"))
                .andRespond(req -> {
                    throw new ResourceAccessException("Connection refused");
                });

        AjaxResult res = proxyService.get("/api/admin/wx-users", Map.of());
        assertEquals(503, res.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(res.get(AjaxResult.MSG_TAG)).contains("无法连接吾喜备婚后端服务"));
    }

    @Test
    void mapsUpstream401To502ToAvoidRuoYiSessionLogoutFalsePositive() {
        mockServer.expect(requestTo("http://127.0.0.1:8081/api/admin/redeem-codes"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":401,\"message\":\"管理凭证无效\",\"data\":null}"));

        AjaxResult res = proxyService.get("/api/admin/redeem-codes", Map.of());
        assertEquals(502, res.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(res.get(AjaxResult.MSG_TAG)).contains("WEDDING_ADMIN_TOKEN"));
    }

    @Test
    void forwardsPayOrdersQueryWithFilterParamsAndMapsSummary() {
        mockServer.expect(requestTo("http://127.0.0.1:8081/api/admin/pay-orders?pageNum=1&status=PAID&envMode=prod"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Admin-Token", "shared_admin_secret_2026"))
                .andRespond(withSuccess(
                        "{\"code\":200,\"message\":\"OK\",\"data\":{\"total\":2,\"rows\":[],\"summary\":{\"paidOrders\":2,\"paidAmountYuan\":\"56.00\"}}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> query = new java.util.LinkedHashMap<>();
        query.put("pageNum", 1);
        query.put("status", "PAID");
        query.put("envMode", "prod");
        query.put("outTradeNo", "");

        AjaxResult res = proxyService.get("/api/admin/pay-orders", query);
        mockServer.verify();
        assertEquals(200, res.get(AjaxResult.CODE_TAG));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) res.get(AjaxResult.DATA_TAG);
        assertEquals(2, data.get("total"));
    }

    @Test
    void controllerForwardsAllPayOrderFilterParameters() {
        mockServer.expect(requestTo(
                        "http://127.0.0.1:8081/api/admin/pay-orders?pageNum=2&pageSize=50&outTradeNo=VP20261124001&openid=oid_1&weddingId=100&status=PAID&envMode=prod"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Admin-Token", "shared_admin_secret_2026"))
                .andRespond(withSuccess(
                        "{\"code\":200,\"message\":\"OK\",\"data\":{\"total\":1,\"rows\":[],\"summary\":{\"totalOrders\":1,\"paidOrders\":1,\"pendingOrders\":0,\"paidAmountYuan\":\"28.00\"}}}",
                        MediaType.APPLICATION_JSON));

        WeddingAdminProxyController controller = new WeddingAdminProxyController(proxyService);
        AjaxResult res = controller.listPayOrders(2, 50, "VP20261124001", "oid_1", 100L, "PAID", "prod");

        mockServer.verify();
        assertEquals(200, res.get(AjaxResult.CODE_TAG));
    }
}

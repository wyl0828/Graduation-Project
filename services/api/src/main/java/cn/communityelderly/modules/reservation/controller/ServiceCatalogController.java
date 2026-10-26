package cn.communityelderly.modules.reservation.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.modules.reservation.dto.ServiceCatalogVO;
import cn.communityelderly.modules.reservation.service.ServiceCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/service-catalogs")
public class ServiceCatalogController {

    private final ServiceCatalogService catalogService;

    public ServiceCatalogController(ServiceCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public Result<List<ServiceCatalogVO>> listActiveCatalogs() {
        return Result.success(catalogService.listActiveCatalogs());
    }
}

package cn.communityelderly.modules.reservation.service;

import cn.communityelderly.modules.reservation.dto.ServiceCatalogVO;
import java.util.List;

public interface ServiceCatalogService {
    List<ServiceCatalogVO> listActiveCatalogs();
}

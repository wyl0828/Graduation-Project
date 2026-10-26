package cn.communityelderly.modules.reservation.service.impl;

import cn.communityelderly.modules.reservation.dto.ServiceCatalogVO;
import cn.communityelderly.modules.reservation.entity.ServiceCatalog;
import cn.communityelderly.modules.reservation.mapper.ServiceCatalogMapper;
import cn.communityelderly.modules.reservation.service.ServiceCatalogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ServiceCatalogServiceImpl implements ServiceCatalogService {

    private final ServiceCatalogMapper catalogMapper;

    public ServiceCatalogServiceImpl(ServiceCatalogMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    @Override
    public List<ServiceCatalogVO> listActiveCatalogs() {
        List<ServiceCatalog> list = catalogMapper.selectList(
                new LambdaQueryWrapper<ServiceCatalog>()
                        .eq(ServiceCatalog::getStatus, "ACTIVE")
                        .orderByAsc(ServiceCatalog::getSortOrder)
        );

        List<ServiceCatalogVO> result = new ArrayList<>();
        for (ServiceCatalog c : list) {
            ServiceCatalogVO vo = new ServiceCatalogVO();
            vo.setCatalogId(c.getCatalogId());
            vo.setServiceType(c.getServiceType());
            vo.setServiceName(c.getServiceName());
            vo.setDescription(c.getDescription());
            vo.setEstimatedDurationMin(c.getEstimatedDurationMin());
            vo.setIconUrl(c.getIconUrl());
            vo.setStatus(c.getStatus());
            vo.setSortOrder(c.getSortOrder());
            result.add(vo);
        }
        return result;
    }
}

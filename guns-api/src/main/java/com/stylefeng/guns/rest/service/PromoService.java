package com.stylefeng.guns.rest.service;

import com.stylefeng.guns.rest.dto.PromoDTO;
import com.stylefeng.guns.rest.vo.BaseResponVO;

public interface PromoService {

    BaseResponVO getPromo(PromoDTO promoDTO);

    BaseResponVO createPromoOrder(Integer promoId, Integer amount, String promoToken);
}

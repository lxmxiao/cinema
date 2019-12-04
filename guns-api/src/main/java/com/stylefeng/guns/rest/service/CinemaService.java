package com.stylefeng.guns.rest.service;

import com.stylefeng.guns.rest.common.exception.CinemaQueryFailException;
import com.stylefeng.guns.rest.vo.BaseResponVO;

import com.stylefeng.guns.rest.vo.cinema.*;

import java.util.List;

public interface CinemaService {

    BaseResponVO getCinemasList(CinemaGetCinemasVO cinemaGetCinemasVO);

    BaseResponVO getConditionList(Integer brandId,Integer hallType,Integer areaId);

    FieldInfo getFieldInfo(Integer cinemaId, Integer fieldId );

    FieldInfo getFields(Integer cinemaId) throws CinemaQueryFailException;

    CinemaInfoVO getCinemaInfoVOByCinemaId(Integer cinemaId);

    CinemaNameAndFilmIdVO getCinemaNameAndFilmIdByFieldId(Integer fieldId);

    List<CinemaPartVO> getPartOfCinemasValue(CinemaGetCinemasVO cinemaGetCinemasVO);
}

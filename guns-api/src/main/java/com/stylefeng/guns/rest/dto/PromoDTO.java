package com.stylefeng.guns.rest.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PromoDTO implements Serializable {

    private Integer brandId;
    private Integer hallType;
    private Integer areaId;
    private Integer pageSize;
    private Integer nowPage;
}

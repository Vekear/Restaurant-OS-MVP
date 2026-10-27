package com.restaurant.storage;

import com.baomidou.mybatisplus.annotation.TableName;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 文件对象 {@code file_object}：local 上传与 OSS 直传确认的统一登记表。
 */
@Getter
@Setter
@TableName("file_object")
public class FileObject extends BaseEntity {

    /** dish / merchant / avatar / table（table 仅内部二维码）。 */
    private String bizType;

    /** 对象键：{@code {bizType}/yyyy/MM/{uuid}.{ext}}，全局唯一。 */
    private String objectKey;

    /** 访问 URL：{@code /files/...}（local）或 OSS 公网地址。 */
    private String url;

    private String originalName;

    private String contentType;

    private Long size;

    /** local / oss。 */
    private String storageType;

    private Long uploaderId;
}

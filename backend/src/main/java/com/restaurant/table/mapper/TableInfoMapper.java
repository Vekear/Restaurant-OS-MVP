package com.restaurant.table.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.table.TableInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 桌台 Mapper：占桌/释放/二维码更新均使用条件UPDATE保证并发安全。 */
@Mapper
public interface TableInfoMapper extends BaseMapper<TableInfo> {

    /** 乐观锁占桌：仅 FREE 且版本一致时成功。 */
    @Update("UPDATE table_info SET status='OCCUPIED', current_session_id=#{sessionId}, "
            + "version=version+1, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{id} AND status='FREE' AND version=#{version} AND deleted=0")
    int occupy(@Param("id") Long id, @Param("sessionId") Long sessionId, @Param("version") Integer version);

    /** 行锁读取桌台，开桌时先加 X 锁避免并发插入会话与外键共享锁互相升级造成死锁。 */
    @Select("SELECT id, merchant_id, table_no, name, zone, capacity, status, current_session_id, qr_code_url, "
            + "qr_salt, qr_version, qr_signed_at, deleted, version, created_at, updated_at "
            + "FROM table_info WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    TableInfo selectForUpdate(@Param("id") Long id);

    /** 释放桌台：仅当仍由该会话占用时成功（幂等释放需调用方先判 FREE）。 */
    @Update("UPDATE table_info SET status='FREE', current_session_id=NULL, "
            + "version=version+1, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{id} AND status='OCCUPIED' AND current_session_id=#{sessionId} "
            + "AND version=#{version} AND deleted=0")
    int release(@Param("id") Long id, @Param("sessionId") Long sessionId, @Param("version") Integer version);

    /** 更新二维码签名（轮换 salt 并递增 qrVersion）。 */
    @Update("UPDATE table_info SET qr_salt=#{salt}, qr_version=#{qrVersion}, qr_code_url=#{qrCodeUrl}, "
            + "qr_signed_at=#{signedAt}, version=version+1, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{id} AND version=#{version} AND deleted=0")
    int updateQr(@Param("id") Long id,
                 @Param("version") Integer version,
                 @Param("salt") String salt,
                 @Param("qrVersion") Integer qrVersion,
                 @Param("qrCodeUrl") String qrCodeUrl,
                 @Param("signedAt") LocalDateTime signedAt);
}

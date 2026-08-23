package io.github.mortogo321.recon.legacy.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.mortogo321.recon.legacy.dto.MerchantRow;

/** Merchant master lookup. Small, slow-changing, heavily read — cached in the service layer. */
@Mapper
public interface MerchantMapper {

    MerchantRow findById(@Param("merchantId") String merchantId);

    List<MerchantRow> findAllById(@Param("merchantIds") Collection<String> merchantIds);

    /** Dynamic search used by the console's merchant picker. */
    List<MerchantRow> search(
            @Param("nameLike") String nameLike,
            @Param("mcc") String mcc,
            @Param("acquirerId") String acquirerId,
            @Param("activeOnly") boolean activeOnly,
            @Param("limit") int limit);
}

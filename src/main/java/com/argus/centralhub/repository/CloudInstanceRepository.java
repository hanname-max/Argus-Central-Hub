package com.argus.centralhub.repository;

import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.enums.InstanceStatus;
import com.argus.centralhub.domain.model.CloudInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CloudInstanceRepository extends JpaRepository<CloudInstance, Long> {

    Optional<CloudInstance> findByInstanceIdAndCloudProvider(String instanceId, CloudProvider cloudProvider);

    List<CloudInstance> findByCloudProvider(CloudProvider cloudProvider);

    List<CloudInstance> findByCloudProviderAndRegion(CloudProvider cloudProvider, String region);

    @Query("SELECT ci FROM CloudInstance ci WHERE ci.cloudProvider = :provider AND ci.instanceId IN :instanceIds")
    List<CloudInstance> findByCloudProviderAndInstanceIds(
            @Param("provider") CloudProvider provider,
            @Param("instanceIds") List<String> instanceIds
    );

    @Query("SELECT ci.instanceId FROM CloudInstance ci WHERE ci.cloudProvider = :provider")
    List<String> findAllInstanceIdsByProvider(@Param("provider") CloudProvider provider);

    @Modifying
    @Transactional
    @Query("UPDATE CloudInstance ci SET ci.status = :newStatus WHERE ci.cloudProvider = :provider AND ci.instanceId NOT IN :excludedIds AND ci.status != :newStatus")
    int markAsTerminatedByProviderAndInstanceIds(
            @Param("provider") CloudProvider provider,
            @Param("excludedIds") List<String> excludedIds,
            @Param("newStatus") InstanceStatus newStatus
    );

    List<CloudInstance> findByStatus(InstanceStatus status);

    @Query("SELECT ci FROM CloudInstance ci WHERE ci.status = :status AND ci.cloudProvider = :provider")
    List<CloudInstance> findByStatusAndCloudProvider(
            @Param("status") InstanceStatus status,
            @Param("provider") CloudProvider provider
    );
}

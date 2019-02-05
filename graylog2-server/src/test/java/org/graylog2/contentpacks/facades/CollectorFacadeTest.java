/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.contentpacks.facades;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.graph.Graph;
import com.lordofthejars.nosqlunit.annotation.UsingDataSet;
import com.lordofthejars.nosqlunit.core.LoadStrategyEnum;
import com.lordofthejars.nosqlunit.mongodb.InMemoryMongoDb;
import org.graylog.plugins.sidecar.rest.models.Collector;
import org.graylog.plugins.sidecar.services.CollectorService;
import org.graylog2.bindings.providers.MongoJackObjectMapperProvider;
import org.graylog2.contentpacks.model.ModelId;
import org.graylog2.contentpacks.model.ModelTypes;
import org.graylog2.contentpacks.model.entities.CollectorEntity;
import org.graylog2.contentpacks.model.entities.Entity;
import org.graylog2.contentpacks.model.entities.EntityDescriptor;
import org.graylog2.contentpacks.model.entities.EntityExcerpt;
import org.graylog2.contentpacks.model.entities.EntityV1;
import org.graylog2.contentpacks.model.entities.EntityWithConstraints;
import org.graylog2.contentpacks.model.entities.NativeEntity;
import org.graylog2.contentpacks.model.entities.NativeEntityDescriptor;
import org.graylog2.contentpacks.model.entities.references.ValueReference;
import org.graylog2.database.MongoConnectionRule;
import org.graylog2.shared.bindings.providers.ObjectMapperProvider;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static com.lordofthejars.nosqlunit.mongodb.InMemoryMongoDb.InMemoryMongoRuleBuilder.newInMemoryMongoDbRule;
import static org.assertj.core.api.Assertions.assertThat;

public class CollectorFacadeTest {
    @ClassRule
    public static final InMemoryMongoDb IN_MEMORY_MONGO_DB = newInMemoryMongoDbRule().build();

    @Rule
    public final MongoConnectionRule mongoRule = MongoConnectionRule.build("collectors");

    private final ObjectMapper objectMapper = new ObjectMapperProvider().get();
    private CollectorService collectorService;
    private CollectorFacade facade;

    @Before
    public void setUp() throws Exception {
        final MongoJackObjectMapperProvider mapperProvider = new MongoJackObjectMapperProvider(objectMapper);
        collectorService = new CollectorService(mongoRule.getMongoConnection(), mapperProvider);
        facade = new CollectorFacade(objectMapper, collectorService);
    }

    @Test
    @UsingDataSet(locations = "/org/graylog2/contentpacks/sidecar_collectors.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void exportNativeEntity() {
        final Collector collector = collectorService.find("5b4c920b4b900a0024af0001");
        final EntityWithConstraints entityWithConstraints = facade.exportNativeEntity(collector);

        assertThat(entityWithConstraints.constraints()).isEmpty();
        final Entity expectedEntity = EntityV1.builder()
                .id(ModelId.of("5b4c920b4b900a0024af0001"))
                .type(ModelTypes.COLLECTOR)
                .data(objectMapper.convertValue(CollectorEntity.create(
                        ValueReference.of("filebeat"),
                        ValueReference.of("exec"),
                        ValueReference.of("linux"),
                        ValueReference.of("/usr/bin/filebeat"),
                        ValueReference.of("/etc/graylog/collector-sidecar/generated/filebeat.yml"),
                        ValueReference.of("-c %s"),
                        ValueReference.of("test config -c %s"),
                        ValueReference.of("")), JsonNode.class))
                .build();
        assertThat(entityWithConstraints.entity()).isEqualTo(expectedEntity);
    }

    @Test
    @UsingDataSet(locations = "/org/graylog2/contentpacks/sidecar_collectors.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void exportEntity() {
        final EntityDescriptor descriptor = EntityDescriptor.create("5b4c920b4b900a0024af0001", ModelTypes.COLLECTOR);

        final EntityWithConstraints entityWithConstraints = facade.exportEntity(descriptor).orElseThrow(AssertionError::new);
        assertThat(entityWithConstraints.constraints()).isEmpty();
        final Entity expectedEntity = EntityV1.builder()
                .id(ModelId.of("5b4c920b4b900a0024af0001"))
                .type(ModelTypes.COLLECTOR)
                .data(objectMapper.convertValue(CollectorEntity.create(
                        ValueReference.of("filebeat"),
                        ValueReference.of("exec"),
                        ValueReference.of("linux"),
                        ValueReference.of("/usr/bin/filebeat"),
                        ValueReference.of("/etc/graylog/collector-sidecar/generated/filebeat.yml"),
                        ValueReference.of("-c %s"),
                        ValueReference.of("test config -c %s"),
                        ValueReference.of("")), JsonNode.class))
                .build();
        assertThat(entityWithConstraints.entity()).isEqualTo(expectedEntity);
    }

    @Test
    @UsingDataSet(loadStrategy = LoadStrategyEnum.DELETE_ALL)
    public void createNativeEntity() {
        final Entity entity = EntityV1.builder()
                .id(ModelId.of("0"))
                .type(ModelTypes.COLLECTOR)
                .data(objectMapper.convertValue(CollectorEntity.create(
                        ValueReference.of("filebeat"),
                        ValueReference.of("exec"),
                        ValueReference.of("linux"),
                        ValueReference.of("/usr/bin/filebeat"),
                        ValueReference.of("/etc/graylog/collector-sidecar/generated/filebeat.yml"),
                        ValueReference.of("-c %s"),
                        ValueReference.of("test config -c %s"),
                        ValueReference.of("")), JsonNode.class))
                .build();

        assertThat(collectorService.count()).isEqualTo(0L);

        final NativeEntity<Collector> nativeEntity = facade.createNativeEntity(entity, Collections.emptyMap(), Collections.emptyMap(), "username");
        assertThat(collectorService.count()).isEqualTo(1L);

        final Collector collector = collectorService.findByName("filebeat");
        assertThat(collector).isNotNull();

        final NativeEntityDescriptor expectedDescriptor = NativeEntityDescriptor.create(entity.id(), collector.id(), ModelTypes.COLLECTOR);
        assertThat(nativeEntity.descriptor()).isEqualTo(expectedDescriptor);
        assertThat(nativeEntity.entity()).isEqualTo(collector);
    }

    @Test
    @UsingDataSet(locations = "/org/graylog2/contentpacks/sidecar_collectors.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void findExisting() {
        final Entity entity = EntityV1.builder()
                .id(ModelId.of("0"))
                .type(ModelTypes.COLLECTOR)
                .data(objectMapper.convertValue(CollectorEntity.create(
                        ValueReference.of("filebeat"),
                        ValueReference.of("exec"),
                        ValueReference.of("linux"),
                        ValueReference.of("/usr/bin/filebeat"),
                        ValueReference.of("/etc/graylog/collector-sidecar/generated/filebeat.yml"),
                        ValueReference.of("-c %s"),
                        ValueReference.of("test config -c %s"),
                        ValueReference.of("")), JsonNode.class))
                .build();

        final NativeEntity<Collector> existingCollector = facade.findExisting(entity, Collections.emptyMap())
                .orElseThrow(AssertionError::new);

        final Collector collector = collectorService.findByName("filebeat");
        assertThat(collector).isNotNull();

        final NativeEntityDescriptor expectedDescriptor = NativeEntityDescriptor.create(entity.id(), collector.id(), ModelTypes.COLLECTOR);
        assertThat(existingCollector.descriptor()).isEqualTo(expectedDescriptor);
        assertThat(existingCollector.entity()).isEqualTo(collector);
    }

    @Test
    @UsingDataSet(locations = "/org/graylog2/contentpacks/sidecar_collectors.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void delete() {
        final Collector collector = collectorService.find("5b4c920b4b900a0024af0001");

        assertThat(collectorService.count()).isEqualTo(3L);
        facade.delete(collector);
        assertThat(collectorService.count()).isEqualTo(2L);
    }

    @Test
    @UsingDataSet(locations = "/org/graylog2/contentpacks/sidecar_collectors.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void createExcerpt() {
        final Collector collector = collectorService.find("5b4c920b4b900a0024af0001");
        final EntityExcerpt excerpt = facade.createExcerpt(collector);

        assertThat(excerpt.id()).isEqualTo(ModelId.of("5b4c920b4b900a0024af0001"));
        assertThat(excerpt.type()).isEqualTo(ModelTypes.COLLECTOR);
        assertThat(excerpt.title()).isEqualTo("filebeat");
    }

    @Test
    @UsingDataSet(locations = "/org/graylog2/contentpacks/sidecar_collectors.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void listEntityExcerpts() {
        final Set<EntityExcerpt> entityExcerpts = facade.listEntityExcerpts();
        assertThat(entityExcerpts).containsOnly(
                EntityExcerpt.builder()
                        .id(ModelId.of("5b4c920b4b900a0024af0001"))
                        .type(ModelTypes.COLLECTOR)
                        .title("filebeat")
                        .build(),
                EntityExcerpt.builder()
                        .id(ModelId.of("5b4c920b4b900a0024af0002"))
                        .type(ModelTypes.COLLECTOR)
                        .title("winlogbeat")
                        .build(),
                EntityExcerpt.builder()
                        .id(ModelId.of("5b4c920b4b900a0024af0003"))
                        .type(ModelTypes.COLLECTOR)
                        .title("nxlog")
                        .build()
        );
    }

    @Test
    @UsingDataSet(locations = "/org/graylog2/contentpacks/sidecar_collectors.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void resolveEntityDescriptor() {
        final EntityDescriptor descriptor = EntityDescriptor.create("5b4c920b4b900a0024af0001", ModelTypes.COLLECTOR);
        final Graph<EntityDescriptor> graph = facade.resolveNativeEntity(descriptor);
        assertThat(graph.nodes()).containsOnly(descriptor);
    }

    @Test
    @UsingDataSet(locations = "/org/graylog2/contentpacks/sidecar_collectors.json", loadStrategy = LoadStrategyEnum.CLEAN_INSERT)
    public void resolveEntity() {
        final Entity entity = EntityV1.builder()
                .id(ModelId.of("0"))
                .type(ModelTypes.COLLECTOR)
                .data(objectMapper.convertValue(CollectorEntity.create(
                        ValueReference.of("filebeat"),
                        ValueReference.of("exec"),
                        ValueReference.of("linux"),
                        ValueReference.of("/usr/bin/filebeat"),
                        ValueReference.of("/etc/graylog/collector-sidecar/generated/filebeat.yml"),
                        ValueReference.of("-c %s"),
                        ValueReference.of("test config -c %s"),
                        ValueReference.of("")), JsonNode.class))
                .build();

        final Graph<Entity> graph = facade.resolveForInstallation(entity, Collections.emptyMap(), Collections.emptyMap());
        assertThat(graph.nodes()).containsOnly(entity);
    }
}
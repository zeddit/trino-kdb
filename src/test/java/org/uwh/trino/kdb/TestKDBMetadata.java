package org.uwh.trino.kdb;

import io.trino.metadata.TableHandle;
import io.trino.spi.connector.*;
import io.trino.spi.statistics.ColumnStatistics;
import io.trino.spi.statistics.TableStatistics;
import io.trino.testing.TestingConnectorSession;
import org.junit.After;
import org.testng.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.*;

public class TestKDBMetadata {
    @BeforeClass
    public static void init() throws Exception {
        kx.c connection = new kx.c("localhost", 8000, "user:password");
        TestKDBPlugin.initKDB(connection);
    }

    ConnectorSession session;
    KDBMetadata sut;

    @BeforeTest
    public void setup() throws Exception {
        kx.c connection = new kx.c("localhost", 8000, "user:password");
        connection.k(".trino.touch:1");
        session = TestingConnectorSession.builder().build();
        KDBClient client = new KDBClient("localhost", 8000, "user", "password");
        sut = new KDBMetadata(client, new Config(Map.of(Config.KDB_USE_STATS_KEY, "true")), new StatsManager(client));
    }

    @AfterMethod
    public void teardown() throws Exception {
        kx.c connection = new kx.c("localhost", 8000, "user:password");
        connection.k("delete stats from `.trino");
    }

    @Test
    public void testListTables() {
        List<SchemaTableName> tables = sut.listTables(session, Optional.empty());

        // SchemaTableName always lower cases - no way to work around :(
        Set<String> expected = Set.of("atable", "btable", "ctable", "dtable", "keyed_table", "splay_table", "attribute_table", "partition_table", "casesensitivetable");

        assertEquals(tables.size(), expected.size());
        assertEquals(tables.stream().map(t -> t.getTableName()).collect(Collectors.toSet()), expected);
    }

    @Test
    public void testColumnAttributes() {
        ConnectorTableHandle handle = sut.getTableHandle(session, new SchemaTableName("default", "attribute_table"));
        Map<String, ColumnHandle> columns = sut.getColumnHandles(session, handle);

        assertEquals(((KDBColumnHandle) columns.get("parted_col")).getAttribute(), Optional.of(KDBAttribute.Parted));
        assertEquals(((KDBColumnHandle) columns.get("grouped_col")).getAttribute(), Optional.of(KDBAttribute.Grouped));
        assertEquals(((KDBColumnHandle) columns.get("sorted_col")).getAttribute(), Optional.of(KDBAttribute.Sorted));
        assertEquals(((KDBColumnHandle) columns.get("unique_col")).getAttribute(), Optional.of(KDBAttribute.Unique));
        assertEquals(((KDBColumnHandle) columns.get("plain_col")).getAttribute(), Optional.empty());
    }

    @Test
    public void testPartitionedTableMetadata() {
        KDBTableHandle handle = (KDBTableHandle) sut.getTableHandle(session, new SchemaTableName("default", "partition_table"));
        Map<String, KDBColumnHandle> columns = (Map) sut.getColumnHandles(session, handle);

        assertTrue(columns.get("date").isPartitionColumn());
        assertTrue(handle.isPartitioned());
        assertEquals(handle.getPartitions(), List.of("2021.05.28", "2021.05.29", "2021.05.30", "2021.05.31"));
    }

    @Test
    public void testTableWithEmptyType() {
        KDBTableHandle handle = (KDBTableHandle) sut.getTableHandle(session, new SchemaTableName("default", "([] a: 1 2 3; b: (`a; 1; 2021.01.01))"));
        Map<String, KDBColumnHandle> columns = (Map) sut.getColumnHandles(session, handle);

        assertEquals(columns.size(), 2);
        assertEquals(columns.values().stream().map(c -> c.getKdbType()).collect(Collectors.toSet()), Set.of(KDBType.Long, KDBType.Unknown));
    }

    @Test
    public void testTableStats() throws Exception {
        ConnectorSession session = TestingConnectorSession.builder().build();
        KDBClient client = new KDBClient("localhost", 8000, "user", "password");
        KDBMetadata metadata = new KDBMetadata(client, new Config(Map.of(Config.KDB_USE_STATS_KEY, "false")), new StatsManager(client));
        TableStatistics stats = metadata.getTableStatistics(session, metadata.getTableHandle(session, new SchemaTableName("default", "atable")), Constraint.alwaysTrue());
        assertEquals(stats, TableStatistics.empty());

        metadata = new KDBMetadata(client, new Config(Map.of(Config.KDB_USE_STATS_KEY, "true")), new StatsManager(client));
        stats = metadata.getTableStatistics(session, metadata.getTableHandle(session, new SchemaTableName("default", "atable")), Constraint.alwaysTrue());
        assertEquals(stats.getRowCount().getValue(), 3.0, 0.1);
    }

    @Test
    public void testPreGeneratedTableStats() throws Exception {
        kx.c connection = new kx.c("localhost", 8000, "user:password");
        connection.k(".trino.stats:([table:`atable`btable] rowcount:10000 20000)");
        connection.k(".trino.colstats:([table:`atable`atable; column:`name`iq] distinct_count:10000 5000; null_fraction: 0.0 0.0; size: 40000 80000; min_value: 0n 50.0; max_value: 0n 300.0)");

        KDBTableHandle handle = (KDBTableHandle) sut.getTableHandle(session, new SchemaTableName("default", "atable"));
        TableStatistics stats = sut.getTableStatistics(session, handle, Constraint.alwaysTrue());

        assertEquals(stats.getRowCount().getValue(), 10000.0, 0.1);
        Map<String, ColumnStatistics> colStats = stats.getColumnStatistics().entrySet().stream().collect(Collectors.toMap(e -> ((KDBColumnHandle) e.getKey()).getName(), e -> e.getValue()));

        ColumnStatistics cstats = colStats.get("name");
        assertEquals(cstats.getNullsFraction().getValue(), 0.0, 0.01);
        assertEquals(cstats.getDistinctValuesCount().getValue(), 10000.0, 0.01);
        assertEquals(cstats.getDataSize().getValue(), 40000.0, 0.1);
        assertTrue(cstats.getRange().isEmpty());

        cstats = colStats.get("iq");
        assertEquals(cstats.getNullsFraction().getValue(), 0.0, 0.01);
        assertEquals(cstats.getDistinctValuesCount().getValue(), 5000.0, 0.01);
        assertEquals(cstats.getDataSize().getValue(), 80000.0, 0.1);
        assertEquals(cstats.getRange().get().getMin(), 50.0, 0.1);
        assertEquals(cstats.getRange().get().getMax(), 300.0, 0.1);
    }

    @Test
    public void testPreGeneratedTableStatsDontExist() throws Exception {
        kx.c connection = new kx.c("localhost", 8000, "user:password");
        connection.k(".trino.stats:([table:`atable`btable] rowcount:10000 20000)");

        KDBTableHandle handle = (KDBTableHandle) sut.getTableHandle(session, new SchemaTableName("default", "ctable"));
        TableStatistics stats = sut.getTableStatistics(session, handle, Constraint.alwaysTrue());
        assertEquals(stats.getRowCount().getValue(), 1000000.0, 0.1);
    }

    @Test
    public void testPartitionedTableStats() {
        KDBTableHandle handle = (KDBTableHandle) sut.getTableHandle(session, new SchemaTableName("default", "partition_table"));
        TableStatistics stats = sut.getTableStatistics(session, handle, Constraint.alwaysTrue());
        assertEquals(stats.getRowCount().getValue(), 12.0, 0.1);
    }
}

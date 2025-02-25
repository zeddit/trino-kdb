package org.uwh.trino.kdb;

import com.google.common.collect.ImmutableMap;
import io.trino.Session;
import io.trino.metadata.SessionPropertyManager;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.BigintType;
import io.trino.testing.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.testng.Assert.*;
import static io.trino.testing.TestingSession.testSessionBuilder;

public class TestKDBPlugin extends AbstractTestQueryFramework {

    public static void initKDB(kx.c conn) throws Exception {
        // create test tables
        conn.k("atable:([] name:`Dent`Beeblebrox`Prefect; iq:98 42 126)");
        conn.k("btable:([] booleans:001b; guids: 3?0Ng; bytes: `byte$1 2 3; shorts: `short$1 2 3; ints: `int$1 2 3; longs: `long$1 2 3; reals: `real$1 2 3; floats: `float$1 2 3; chars:\"ab \"; strings:(\"hello\"; \"world\"; \"trino\"); symbols:`a`b`c; timestamps: `timestamp$1 2 3; months: `month$1 2 3; dates: `date$1 2 3; datetimes: `datetime$1 2 3; timespans: `timespan$1 2 3; minutes: `minute$1 2 3; seconds: `second$1 2 3; times: `time$1 2 3 )");
        conn.k("ctable:([] const:1000000#1; linear:til 1000000; sym:1000000#`hello`world`trino; s:1000000#string `hello`world`trino)");
        conn.k("dtable:([] num:1 2 3; num_array: (1 2 3; 3 4 5; 6 7 8))");
        conn.k("keyed_table:([name:`Dent`Beeblebrox`Prefect] iq:98 42 126)");
        conn.k("attribute_table:([] unique_col: `u#`a`b`c; sorted_col: `s#1 2 3; parted_col: `p#1 1 2; grouped_col: `g#`a`b`c; plain_col: 1 2 3)");
        conn.k("CaseSensitiveTable:([] Symbol: `a`a`b`b; Number: 1 2 3 4; Square: 1 4 9 16)");

        conn.k("ltable:([] id:`long$(); name: (); typ: `symbol$(); typ2: `symbol$(); rating: ())");
        conn.k("lsource:([] id:`long$til 500000; name: string til 500000; typ: 500000?(`great`expectation`oliver`twist); typ2: 500000?(`great`expectation`oliver`twist); rating: 500000?(\"A+\"; \"A-\"; \"B+\"))");

        conn.k("itable:([] num:`long$(); sym: `symbol$())");
        conn.k("ikeytable:([sym: `symbol$()] num:`long$())");
        conn.k("longitable:([] " +
                " booleans:`boolean$();" +
                " bytes:`byte$();" +
                " shorts:`short$();" +
                " ints:`int$();" +
                " longs:`long$();" +
                " reals:`real$();" +
                " floats:`float$();" +
                " chars: `char$(); " +
                " syms:`symbol$();" +
                " strings: ();" +
                " dates: `date$();" +
                " timestamps: `timestamp$();" +
                " datetimes: `datetime$())");
        conn.k("CaseITable:([] Num:`long$(); Sym: `symbol$())");
        conn.k(".myns.instable:([] num:`long$(); sym: `symbol$())");
        // inserts everything twice
        conn.k("myupd:{[t; data] insert[t;data]; insert[t;data]}");

        conn.k(".myns.atable:([] name:`Dent`Beeblebrox`Prefect`Marvin; iq:98 42 126 300)");
        conn.k(".myns.BTable:([] name:`Dent`Beeblebrox`Prefect`Marvin; iq:98 42 126 300)");
        conn.k(".myns.ctable:([] name:`Dent`Beeblebrox`Prefect`Marvin; iq:98 42 126 300)");
        conn.k(".CaseNS.casenstable:([] name:`Dent`Beeblebrox`Prefect`Marvin; iq:98 42 126 300)");

        conn.k("tfunc:{[] atable}");
        Path tempp = Files.createTempDirectory("splay");
        Path p = tempp.resolve("splay_table");
        String dirPath = p.toAbsolutePath().toString();
        conn.k("`:" + dirPath + "/ set ([] v1:10 20 30; v2:1.1 2.2 3.3)");
        conn.k("\\l "+dirPath);

        Path p2 = tempp.resolve("db");
        dirPath = p2.toAbsolutePath().toString();
        Files.createDirectories(p2);
        System.out.println(dirPath);
        conn.k("`:"+dirPath + "/2021.05.28/partition_table/ set ([] v1:10 20 30; v2:1.1 2.2 3.3)");
        conn.k("`:"+dirPath + "/2021.05.29/partition_table/ set ([] v1:10 20 30; v2:1.1 2.2 3.3)");
        conn.k("`:"+dirPath + "/2021.05.30/partition_table/ set ([] v1:10 20 30; v2:1.1 2.2 3.3)");
        conn.k("`:"+dirPath + "/2021.05.31/partition_table/ set ([] v1:10 20 30; v2:1.1 2.2 3.3)");
        conn.k("\\l " + dirPath);
    }

    @BeforeClass
    public static void setupKDB() throws Exception {
        kx.c conn = new kx.c("localhost", 8000, "user:password");
        initKDB(conn);

        Logger.getLogger(KDBClient.class.getName()).addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                String msg = record.getMessage();
                if (msg.startsWith("KDB query: ")) {
                    lastQuery = msg.substring("KDB query: ".length());
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        });
    }

    @AfterMethod
    public void cleanup() throws Exception {
        kx.c conn = new kx.c("localhost", 8000, "user:password");
        conn.k("delete from `itable");
        conn.k("delete from `longitable");
        conn.k("delete from `CaseITable");
        conn.k("delete from `.myns.instable");
    }

    // this test kills the local KDB instance and is a bit of a pain to run, hence disabled by default
    @Test(enabled = false)
    public void testReconnect() throws Exception {
        kx.c conn = new kx.c("localhost", 8000, "user:password");
        String command = new String((char[]) conn.k("(system \"pwd\")[0] , \"/\" , \" \" sv .z.X"));
        conn.ks("exit 0");

        Runtime.getRuntime().exec(command);
        Thread.sleep(50);

        conn = new kx.c("localhost", 8000, "user:password");
        initKDB(conn);

        query("select * from atable", 3);
    }

    @Test
    public void testKDBDownDoesNotStopCatalogCreation() throws Exception {
        DistributedQueryRunner qrunner = DistributedQueryRunner.builder(createSession("default"))
                .setNodeCount(1)
                .setExtraProperties(ImmutableMap.of())
                .build();

        qrunner.installPlugin(new KDBPlugin());
        qrunner.createCatalog("kdb", "kdb",
                ImmutableMap.<String,String>builder()
                        .put("kdb.host", "localhost")
                        .put("kdb.port", "12345")
                        .build());

        // only throw exception once operation is invoked
        try {
            qrunner.execute(createSession("default"), "show tables");
            fail();
        } catch (Exception e) {
            LOGGER.info(e.toString());
        }
    }

    @Test
    public void testQuery() {
        query("select * from atable", 3);
        assertLastQuery("select [50000] name, iq from atable");
        assertResultColumn(0, Set.of("Dent", "Beeblebrox", "Prefect"));
    }

    @Test
    public void testPagination() {
        query("select linear from ctable limit 120000", 120000);
        assertLastQuery("select [100000 20000] linear from ctable where i<120000");

        Session session = Session.builder(getSession()).setCatalogSessionProperty("kdb", "page_size", "70000").build();
        query(session, "select linear from ctable limit 120000", 120000);
        assertLastQuery("select [70000 50000] linear from ctable where i<120000");
    }

    @Test
    public void testLargeCountQuery() {
        query("select count(*) from ctable", 1);
        assertEquals(res.getOnlyColumnAsSet(), Set.of(1_000_000L));

        query("select sum(linear) from ctable", 1);
        assertEquals(res.getOnlyColumnAsSet(), Set.of(499999500000L));
    }

    @Test
    public void testPassThroughQuery() {
        query("select * from kdb.default.\"select max iq from atable\"", 1);
        assertLastQuery("select [50000] iq from (select max iq from atable)");
        assertEquals(res.getOnlyColumnAsSet(), Set.of(126L));
    }

    @Test
    public void testSplayTableQuery() {
        query("select * from splay_table", 3);
        assertLastQuery("select [50000] v1, v2 from splay_table");
        assertResultColumn(0, Set.of(10L, 20L, 30L));
    }

    @Test
    public void testTableWithUnknownType() {
        query("select * from \"([] a: 1 2 3; b: (`a; 1; 2021.01.01))\"", 3);
        assertResultColumn(1, Set.of("a", "1", "2021-01-01"));
    }


    @Test
    public void testFilterPushdown() {
        query("select * from atable where iq > 50", 2);
        assertLastQuery("select [50000] name, iq from atable where iq > 50");
        assertResultColumn(0, Set.of("Dent", "Prefect"));
    }

    @Test
    public void testFilterPushdownMultiple() {
        query("select * from atable where iq > 50 and iq < 100", 1);
        assertLastQuery("select [50000] name, iq from atable where (iq > 50) & (iq < 100)");
        assertResultColumn(0, Set.of("Dent"));
    }

    @Test
    public void testFilterPushdownSymbol() {
        query("select * from atable where name = 'Dent'", 1);
        assertLastQuery("select [50000] name, iq from atable where name = `Dent");
        assertResultColumn(0, Set.of("Dent"));
    }

    @Test
    public void testFilterWithAttributes() {
        query("select * from \"([] id:`s#0 1 2; name:`alice`bob`charlie; age:28 35 28)\" where age = 28 and id = 0", 1);
        assertLastQuery("select [50000] id, name, age from ([] id:`s#0 1 2; name:`alice`bob`charlie; age:28 35 28) where id = 0, age = 28");
    }

    @Test
    public void testAggregationPushdown() {
        // count(*)
        query("select count(*) from atable", 1);
        assertResultColumn(0, Set.of(3L));
        assertLastQuery("select [50000] col0 from (select col0: count i from atable)");

        // sum(_)
        query("select sum(iq) from atable", 1);
        assertResultColumn(0, Set.of(98L+42L+126L));
        assertLastQuery("select [50000] col0 from (select col0: sum iq from atable)");

        // sum(_) and count(*)
        query("select sum(iq), count(*) from atable", 1);
        assertResultColumn(0, Set.of(98L+42L+126L));
        assertResultColumn(1, Set.of(3L));
        assertLastQuery("select [50000] col0, col1 from (select col0: sum iq, col1: count i from atable)");

        // count(distinct)
        query("select count(distinct sym) from \"([] sym: `a`a`b)\"", 1);
        assertResultColumn(0, Set.of(2L));
        assertLastQuery("select [50000] col0 from (select col0: count sym from (select count i by sym from ([] sym: `a`a`b)))");

        // count(distinct) #2
        query("select sym, count(distinct sym2) from \"([] sym: `a`a`b`b; sym2: `a`b`c`c)\" group by sym", 2);
        assertResultColumn(1, Set.of(1L, 2L));
        assertLastQuery("select [50000] sym, col0 from (select col0: count sym2 by sym from (select count i by sym, sym2 from ([] sym: `a`a`b`b; sym2: `a`b`c`c)))");

        // sum group by
        query("select sym, sum(num) from \"([] sym: `a`a`b; num: 2 3 4)\" group by sym", 2);
        assertResultColumn(0, Set.of("a","b"));
        assertResultColumn(1, Set.of(5L, 4L));
        assertLastQuery("select [50000] sym, col0 from (select col0: sum num by sym from ([] sym: `a`a`b; num: 2 3 4))");

        // sum group by multiple
        query("select sym, sym2, sum(num) from \"([] sym: `a`a`b`b; sym2: `a`a`b`c; num: 2 3 4 6)\" group by sym, sym2", 3);
        assertResultColumn(2, Set.of(5L, 4L, 6L));
        assertLastQuery("select [50000] sym, sym2, col0 from (select col0: sum num by sym, sym2 from ([] sym: `a`a`b`b; sym2: `a`a`b`c; num: 2 3 4 6))");

        // aggregation with filter
        query("select sym, sum(num) from \"([] sym: `a`a`b`b; sym2: `a`b`a`b; num: 2 3 4 5)\" where sym2 = 'a' group by sym", 2);
        assertResultColumn(0, Set.of("a", "b"));
        assertResultColumn(1, Set.of(2L, 4L));

        // aggregation with nested limit
        query("select sym, sum(num) from (select * from \"([] sym: `a`a`a; num: 2 3 4)\" limit 2) t group by sym", 1);
        assertResultColumn(1, Set.of(5L));
        assertLastQuery("select [50000] sym, col0 from (select col0: sum num by sym from ([] sym: `a`a`a; num: 2 3 4) where i<2)");

        query("select sym, sum(num) from (select * from \"([] sym: `a`a`a; sym2: `a`b`b; num: 2 3 4)\" where sym2 = 'b' limit 1) t group by sym", 1);
        assertResultColumn(1, Set.of(3L));
        assertLastQuery("select [50000] sym, col0 from (select col0: sum num by sym from (select [1] from ([] sym: `a`a`a; sym2: `a`b`b; num: 2 3 4) where sym2 = `b))");
    }

    @Test
    public void aggregation_functions() {
        Session noPushdownSession = Session.builder(getSession()).setCatalogSessionProperty("kdb", "push_down_aggregation", "false").build();

        Map<String,String> expectedQueries = ImmutableMap.<String,String>builder()
                .putAll(Map.of(
                        "select avg(num) from \"([] num: 1 2 3 4 0N)\"", "select [50000] col0 from (select col0: avg num from ([] num: 1 2 3 4 0n))",
                        "select max(num) from \"([] num: 1 2 3 4 0N)\"", "select [50000] col0 from (select col0: max num from ([] num: 1 2 3 4 0n))",
                        "select min(num) from \"([] num: 1 2 3 4 0N)\"", "select [50000] col0 from (select col0: min num from ([] num: 1 2 3 4 0n))",
                        "select stddev(num) from \"([] num: 1 2 3 4 0N)\"", "select [50000] col0 from (select col0: sdev num from ([] num: 1 2 3 4 0n))",
                        "select stddev_samp(num) from \"([] num: 1 2 3 4 0N)\"", "select [50000] col0 from (select col0: sdev num from ([] num: 1 2 3 4 0n))",
                        "select stddev_pop(num) from \"([] num: 1 2 3 4 0N)\"", "select [50000] col0 from (select col0: dev num from ([] num: 1 2 3 4 0n))",
                        "select variance(num) from \"([] num: 1 2 3 4 0N)\"", "select [50000] col0 from (select col0: svar num from ([] num: 1 2 3 4 0n))",
                        "select var_samp(num) from \"([] num: 1 2 3 4 0N)\"", "select [50000] col0 from (select col0: svar num from ([] num: 1 2 3 4 0n))",
                        "select var_pop(num) from \"([] num: 1 2 3 4 0N)\"", "select [50000] col0 from (select col0: var num from ([] num: 1 2 3 4 0n))"))
                .putAll(Map.of(
                        "select every(b) from \"([] b: 110b)\"", "select [50000] col0 from (select col0: all b from ([] b: 110b))",
                        "select bool_and(b) from \"([] b: 110b)\"", "select [50000] col0 from (select col0: all b from ([] b: 110b))",
                        "select bool_or(b) from \"([] b: 110b)\"", "select [50000] col0 from (select col0: any b from ([] b: 110b))",
                        "select count_if(b) from \"([] b: 110b)\"", "select [50000] col0 from (select col0: sum `long$ b from ([] b: 110b))"
                )).build();

        for (Map.Entry<String,String> e : expectedQueries.entrySet()) {
            query(noPushdownSession, e.getKey(), 1);
            Object expected = res.getOnlyValue();
            String trinoQuery = lastQuery;

            query(e.getKey(), 1);

            if (expected instanceof Double) {
                assertEquals((double) expected, (double) res.getOnlyValue(), 0.01);
            } else {
                assertEquals(expected, res.getOnlyValue());
            }
            assertLastQuery(e.getValue());
            assertNotEquals(trinoQuery, lastQuery);
        }
    }

    @Test
    public void aggregation_with_upper_case_columns() {
        query("select Symbol, sum(Number) from CaseSensitiveTable group by Symbol", 2);
        assertResultColumn(0, Set.of("a", "b"));
        assertResultColumn(1, Set.of(3L, 7L));
    }

    @Test
    public void testSessionPushDownAggregationOverride() {
        Session session = Session.builder(getSession()).setCatalogSessionProperty("kdb", "push_down_aggregation", "false").build();
        query(session, "select count(*) from atable");
        assertLastQuery("select [50000] i from atable");
    }

    @Test
    public void testVirtualTableOverride() {
        Session session = Session.builder(getSession()).setCatalogSessionProperty("kdb", "virtual_tables", "true").build();
        query(session, "select * from atable limit 2");
        assertLastQuery("select [2] from select name, iq from atable where i<2");
    }

    @Test
    public void testDescribe() {
        query("describe atable", 2);
        assertResultColumn(0, Set.of("name","iq"));
    }

    @Test
    public void testTypeSupport() {
        query("select * from btable", 3);

        query("select strings, symbols from btable where strings = 'hello'", 1);
        assertLastQuery("select [50000] strings, symbols from btable where strings like \"hello\"");

        query("select strings, symbols from btable where strings = 'h'", 0);
        assertLastQuery("select [50000] strings, symbols from btable where strings like (enlist \"h\")");

        query("select dates, symbols from btable where dates = DATE '2000-01-02'", 1);
        assertLastQuery("select [50000] symbols, dates from btable where dates = 2000.01.02");

        query("select dates, symbols from btable where dates BETWEEN DATE '2000-01-01' AND DATE '2000-01-03'", 2);
        assertLastQuery("select [50000] symbols, dates from btable where dates within 2000.01.01 2000.01.03");
    }

    @Test
    public void testNullHandling() {
        query("select * from \"([] ds:(2021.05.30; 0nd; 2021.05.31))\"",3);
        List<MaterializedRow> rows = res.getMaterializedRows();
        assertEquals(rows.get(0).getField(0), LocalDate.of(2021,5,30));
        assertNull(rows.get(1).getField(0));
        assertEquals(rows.get(2).getField(0), LocalDate.of(2021,5,31));

        query("select * from \"([] " +
                        "type_g:(0Ng; 0x0 sv 16?0xff); " +
                        "type_h: (0Nh; 1h); " +
                        "type_i: (0Ni; 1i); " +
                        "type_j: (0Nj; 1j); " +
                        "type_e: (0Ne; 1.0e); " +
                        "type_f: (0Nf; 1.0); " +
                        "type_s: ``abc; " +
                        "type_p: (0Np; `timestamp$1); " +
                        "type_m: (0Nm; 2020.01m); " +
                        "type_d: (0Nd; 2020.01.01); " +
                        "type_z: (0Nz; .z.z); " +
                        "type_n: (0Nn; `timespan$1); " +
                        "type_u: (0Nu; `minute$1); " +
                        "type_v: (0Nv; `second$1); " +
                        "type_t: (0Nt; `time$1)" +
                        ")\"",
                2);
        rows = res.getMaterializedRows();
        for (int i=0; i<rows.get(0).getFieldCount(); i++) {
            assertNull(rows.get(0).getField(i));
            assertNotNull(rows.get(1).getField(i));
        }

        query("select * from \"([] dates:(2021.05.31 0Nd; 2021.01.01 2021.01.02))\"", 2);
        List list = (List) res.getMaterializedRows().get(0).getField(0);
        assertNull(list.get(1));
    }

    @Test
    public void testTimeTypes() {
        query("select * from \"([] t: (23:55:00.000; 23:59:59.000))\"",2);
        assertResultColumn(0, Set.of(LocalTime.of(23,55,00), LocalTime.of(23,59,59)));

        query("select * from \"([] t: (2021.05.31\\T23:55:00; 2021.06.01\\T01:00:00))\"", 2);
        assertResultColumn(0, Set.of(
                LocalDateTime.of(2021,5,31, 23,55,00),
                LocalDateTime.of(2021, 6,1, 1,0,0)));

        query("select * from \"([] t: (2021.05.31\\D23:55:00; 2021.06.01\\D01:00:00))\"", 2);
        assertResultColumn(0, Set.of(
                LocalDateTime.of(2021,5,31, 23,55,00),
                LocalDateTime.of(2021, 6,1, 1,0,0)));
    }


    @Test
    public void testFilterOrdering() {
        query("select count(*) from ctable where s = 'trino' and sym = 'trino'", 1);
        assertLastQuery("select [50000] col0 from (select col0: count i from ctable where sym = `trino, s like \"trino\")");
    }

    @Test
    public void testKeyedTableQuery() {
        query("select * from keyed_table", 3);

        // pass through query
        query("select * from \"select from keyed_table\"", 3);
    }

    @Test
    public void testStoredProc() {
        query("select * from \"tfunc[]\"", 3);
    }

    @Test
    public void testLimit() {
        query("select * from atable limit 2", 2);
        assertLastQuery("select [2] name, iq from atable where i<2");

        query("select const, linear from ctable where const = 1 limit 10", 10);
        assertLastQuery("select [10] const, linear from ctable where const = 1");

        query("select * from \"select from atable\" limit 2", 2);
        assertLastQuery("select [2] name, iq from (select from atable) where i<2");

        // test limit greater than matching rows
        query("select name from atable where iq < 100 limit 10", 2);
        assertResultColumn(0, Set.of("Dent", "Beeblebrox"));
        assertLastQuery("select [10] name from atable where iq < 100");
    }

    @Test
    public void testNestedArray() {
        // array of long
        query("select * from dtable", 3);
        assertEquals(res.getTypes(), List.of(BigintType.BIGINT, new ArrayType(BigintType.BIGINT)));

        // array of double
        query("select * from \"([] col:(1.0 2.0; 3.0 4.0))\"", 2);
        // array of symbols
        query("select * from \"([] col:(`a`b`c; `d`e`f))\"", 2);
    }

    @Test
    public void testPartitionedTable() {
        query("select count(*) from partition_table", 1);
        // 3 rows * 4 days
        assertEquals(res.getOnlyColumnAsSet(), Set.of(12L));

        // 3 rows in one date
        query("select count(*) from partition_table where date = DATE '2021-05-28'", 1);
        assertEquals(res.getOnlyColumnAsSet(), Set.of(3L));

        query("select * from partition_table where date = DATE '2021-05-28' limit 100", 3);

    }

    @Test
    public void testSymbolWithSpaces() {
        query("select sym from \"([] sym:(`with; `space; `$\"\"with space\"\"))\" where sym = 'with space'", 1);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("with space"));

        query("select sym from \"([] sym:(`with; `space; `$\"\"with space\"\"))\" where sym in ('with', 'with space')", 2);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("with", "with space"));

        query("select sym from \"([] sym:(`with; `space; `$\"\"with-dash\"\"))\" where sym = 'with-dash'", 1);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("with-dash"));
    }

    private void testLikePattern(String syms, String pattern, boolean expectLike) {
        String sql = "select sym from \"([] sym:"+syms + ")\" where sym like '" + pattern + "'";
        Session session = Session.builder(getSession()).setCatalogSessionProperty("kdb", "push_down_like", "false").build();
        Set oracle = computeActual(session, sql).getOnlyColumnAsSet();
        assertFalse(lastQuery.contains("like"));
        session = Session.builder(getSession()).setCatalogSessionProperty("kdb", "push_down_like", "true").build();
        Set sut = computeActual(session, sql).getOnlyColumnAsSet();
        assertEquals(lastQuery.contains("like"), expectLike);
        System.out.println(oracle + " vs " + sut);
        assertEquals(sut, oracle);
    }

    @Test
    public void testLikeQuery() {
        Session session = Session.builder(getSession()).setCatalogSessionProperty("kdb", "push_down_like", "true").build();
        query(session, "select sym from \"([] sym:`with`without`out)\" where sym like '%with%'", 2);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("with", "without"));
        assertTrue(lastQuery.contains("sym like \"*with*\""));

        query(session, "select s from \"([] s:(\"\"with\"\"; \"\"without\"\"; \"\"out\"\"))\" where s like '%with%'", 2);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("with", "without"));
        assertTrue(lastQuery.contains("s like \"*with*\""));

        query(session, "select sym from \"([] sym:`with`without`out)\" where sym not like '%with%'", 1);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("out"));
        assertFalse(lastQuery.contains("like"));

        query(session, "select sym from \"([] sym:`wit_out`without)\" where sym like 'wit$_out' ESCAPE '$'", 1);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("wit_out"));
        assertFalse(lastQuery.contains("like"));

        query(session, "select sym from \"([] sym:`\\U\\P\\P\\E\\R`lower)\" where upper(sym) like '%ER'", 2);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("UPPER", "lower"));
        assertFalse(lastQuery.contains("like"));

        query(session, "select sym from \"([] sym:`with`without`out)\" where sym like '%wi%' and sym like '%out'", 1);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("without"));
        // Not yet supported -> two likes get passed into the ConnectorMetadata as a LogicalExpression[AND] of two LikeExpressions
        assertFalse(lastQuery.contains("like"));

        // special case 'wi%' gets translated into >= wi, < wj
        query(session, "select sym from \"([] sym:`with`without`out)\" where sym like 'wi%' and sym like '%out'", 1);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("without"));

        // special case 'wi%' gets translated into >= wi, < wj, for strings
        query(session, "select s from \"([] s:(\"\"with\"\"; \"\"without\"\"; \"\"out\"\"))\" where s like 'wi%'", 2);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("with", "without"));

        testLikePattern("`with`without`out", "_ith", true);
        testLikePattern("(`a; `$\"\"symbol*\"\")", "%*", true);
        testLikePattern("(`ab; `$\"\"symbol?\"\")", "%?", true);
        testLikePattern("(`ab; `$\"\"hello \\\\\"\" world\"\")", "%\"%", true);
        testLikePattern("(`ab; `$\"\"symbol[\"\")", "%[", true);
        testLikePattern("(`ab; `$\"\"symbol]\"\")", "%]", true);
        // trino converts this into an '=' operator
        testLikePattern("`a`b`c", "a", false);
        testLikePattern("`\\U\\P\\P\\E\\R`lower", "%ER", true);
    }

    @Test
    public void testPartitionedTableQuerySplit() {
        query("select * from partition_table", 12);
        assertTrue(Set.of(
                "select [50000] from select date, v1, v2 from partition_table where date = 2021.05.28",
                "select [50000] from select date, v1, v2 from partition_table where date = 2021.05.29",
                "select [50000] from select date, v1, v2 from partition_table where date = 2021.05.30",
                "select [50000] from select date, v1, v2 from partition_table where date = 2021.05.31"
        ).contains(lastQuery));
    }

    @Test
    public void testCaseSensitiveTable() {
        query("select * from CaseSensitiveTable", 4);
    }

    // no solution yet for dynamic queries
    @Test
    public void testCaseSensitiveQuery() {
        query("select * from \"select from \\Case\\Sensitive\\Table\"", 4);
    }

    @Test
    public void testNamespaceQuery() {
        query("select * from myns.atable", 4);
        query("select * from myns.btable", 4);
        query("select * from myns.ctable", 4);
        query("select * from casens.casenstable", 4);
    }

    @Test
    public void testMetadataQueries() {
        query("show schemas from kdb", 6);
        assertEquals(Set.of("default", "myns", "casens", "o", "trino", "information_schema"),res.getOnlyColumnAsSet());

        query("show tables from kdb.myns", 4);
        query("show tables from kdb.casens", 1);
    }

    @Test
    public void testUntypedColumnWithStrings() {
        query("select col from \"([] col:(`sym; \"\"hello\"\"; 1))\"", 3);
        assertEquals(res.getOnlyColumnAsSet(), Set.of("sym", "hello", "1"));
    }

    /*
    Insertion related tests
     */

    @Test
    public void testInsertSimple() {
        query("insert into itable values (1, 'ibm'), (2, 'msft')");
        query("select * from itable", 2);
    }

    @Test
    public void testInsertColumnReorder() {
        query("insert into itable (sym, num) values ('ibm', 1), ('msft', 2)");
        query("select sym from itable", 2);
        assertEquals(Set.of("ibm", "msft"), res.getOnlyColumnAsSet());
    }

    @Test
    public void testInsertNulls() {
        query("insert into itable values (1, 'ibm'), (null, 'msft'), (3, null)");

        query("select num, sym from itable", 3);
        List<MaterializedRow> rows = res.getMaterializedRows();
        assertEquals(List.of(1L, "ibm"), rows.get(0).getFields());

        assertNull(rows.get(1).getField(0));
        assertEquals("msft", rows.get(1).getField(1));

        assertEquals(3L, rows.get(2).getField(0));
        assertNull(rows.get(2).getField(1));

        query("insert into longitable (booleans, bytes, shorts, ints, longs, reals, floats, chars, syms, strings, dates, timestamps, datetimes) values " +
                "(NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)", 1);
        query("select * from longitable", 1);
        MaterializedRow row = res.getMaterializedRows().get(0);
        assertFalse((boolean) row.getField(0));
        assertEquals((byte) 0, row.getField(1));
        assertNull(row.getField(2));
        assertNull(row.getField(3));
        assertNull(row.getField(4));
        assertNull(row.getField(5));
        assertNull(row.getField(6));
        assertEquals(" ", row.getField(7));
        assertNull(row.getField(8));
        assertEquals("", row.getField(9));
        assertNull(row.getField(10));
        assertNull(row.getField(11));
        assertNull(row.getField(12));
    }

    @Test
    public void testInsertCaseSensitive() {
        query("insert into CaseITable (Num, Sym) values (1, 'ibm'), (2, 'msft')");
        query("select sym from caseitable", 2);
        assertEquals(Set.of("ibm", "msft"), res.getOnlyColumnAsSet());
    }

    @Test
    public void testInsertNamespace() {
        query("insert into kdb.myns.instable values (1, 'ibm'), (2, 'msft')");
        query("select sym from kdb.myns.instable", 2);
        assertEquals(Set.of("ibm", "msft"), res.getOnlyColumnAsSet());
    }

    @Test
    public void testInsertWithTypes() {
        query("insert into longitable (booleans, bytes, shorts, ints, longs, reals, floats, chars, syms, strings, dates, timestamps, datetimes) values " +
                "(TRUE, 1, 10, 100, 1000, 0.1, 0.01, 'c', 'ibm', 'hello', date '2022-01-01', TIMESTAMP '2022-01-01 01:00:00', TIMESTAMP '2022-01-01 02:00:00')", 1);

        query("select * from longitable", 1);
        MaterializedRow row = res.getMaterializedRows().get(0);
        assertEquals(true, row.getField(0));
        assertEquals((byte) 1, row.getField(1));
        assertEquals((short) 10, row.getField(2));
        assertEquals(100, row.getField(3));
        assertEquals(1000L, row.getField(4));
        assertEquals(0.1, (double) row.getField(5), 0.0001);
        assertEquals(0.01, (double) row.getField(6), 0.0001);
        assertEquals("c", row.getField(7));
        assertEquals("ibm", row.getField(8));
        assertEquals("hello", row.getField(9));
        assertEquals(LocalDate.of(2022, 1, 1), row.getField(10));
        assertEquals(LocalDateTime.of(2022, 1, 1, 1, 0), row.getField(11));
        assertEquals(LocalDateTime.of(2022, 1, 1, 2, 0), row.getField(12));
    }

    @Test
    public void testNotAllowedInsertIntoPartitioned() {
        try {
            query("insert into partition_table select * from partition_table", 3);
            fail("Expected to fail");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("KDB connector does not support insert into partitioned table"));
        }
    }

    @Test
    public void testLargeInsert() {
        query("insert into itable select num, sym from \"([] num: til 1000000; sym: 1000000#`ibm)\"");
        query("select count(*) from itable", 1);
        assertEquals(1000000L, res.getOnlyValue());
    }

    @Test
    public void testLargeInsert2() {
        query("insert into ltable select * from lsource");
        query("select count(*) from ltable", 1);
        assertEquals(500000L, res.getOnlyValue());
    }

    @Test
    public void testInsertIntoKeyedTable() {
        query("insert into ikeytable (sym, num) values ('ibm', 1), ('msft', 2)");
        query("select sym from ikeytable", 2);
        assertEquals(Set.of("ibm", "msft"), res.getOnlyColumnAsSet());
    }

    @Test
    public void testInsertCustomInsertFunction() {
        Session session = Session.builder(getSession()).setCatalogSessionProperty("kdb", "insert_function", "myupd").build();
        query(session, "insert into itable (sym, num) values ('ibm', 1), ('msft', 2)");
        query("select sym from itable", 4);
        assertEquals(Set.of("ibm", "msft"), res.getOnlyColumnAsSet());
    }

    @Test
    public void testTypesInFilter() {
        query("select * from \"([] sym: `a`b`c; time:`time$(09:00:00; 12:00:00; 15:00:00))\" where time <= TIME '13:00'", 2);
        assertTrue(lastQuery.contains("time <="));

        query("select * from \"([] sym: `a`b`c; month:(2020.02m; 2021.02m; 2022.02m))\" where month <= '2021-05'", 2);
        assertFalse(lastQuery.contains("month <="));

        query("select * from \"([] sym: `a`b`c; bs: 110b)\" where bs = TRUE", 2);
        query("select * from \"([] sym: `a`b`c; bs: 110b)\" where bs = FALSE", 1);
        assertTrue(lastQuery.contains("bs = 0b"));

        query("select * from \"([] sym: `a`b`c; num:(1.0; 2.0; 3.0))\" where num < 2.5", 2);
        assertTrue(lastQuery.contains("num <"));

        query("select * from \"([] sym: `a`b`c; num:`real$(1.0; 2.0; 3.0))\" where num < 2.5", 2);
        assertTrue(lastQuery.contains("num <"));

        query("select * from \"([] sym: `a`b`c; num:(1; 2; 3))\" where num <= 2", 2);
        assertTrue(lastQuery.contains("num <="));

        query("select * from \"([] sym: `a`b`c; num:`int$(1; 2; 3))\" where num <= 2", 2);
        assertTrue(lastQuery.contains("num <="));

        query("select * from \"([] sym: `a`b`c; num:`short$(1; 2; 3))\" where num <= 2", 2);
        assertTrue(lastQuery.contains("num <="));

        query("select * from \"([] sym: `a`b`c; num:`byte$(1; 2; 3))\" where num <= 2", 2);
        assertTrue(lastQuery.contains("num <="));

        query("select * from \"([] sym: `a`b`c; t:(2000.01.01\\D00:00:00; 2010.01.01\\D00:00:00; 2020.01.01\\D00:00:00))\" where t <= TIMESTAMP '2015-01-01 00:00'", 2);
        assertTrue(lastQuery.contains("t <= "));

        query("select * from \"([] sym: `a`b`c; t:(2000.01.01\\T00:00:00; 2010.01.01\\T00:00:00; 2020.01.01\\T00:00:00))\" where t <= TIMESTAMP '2015-01-01 00:00'", 2);
        assertTrue(lastQuery.contains("t <= "));
    }

    @Test
    public void testNativeQuery() {
        query("select * from TABLE(system.query(query => 'select from atable'))", 3);
    }

    @Test
    public void testNativeQueryCaseSensitive() {
        query("select * from TABLE(system.query(query => 'select from CaseSensitiveTable'))", 4);
    }

    @Test
    public void testPartialPushdown() {
        query("select * from \"([] sym: `a`a`c; ms: `minute$1 2 3)\" where sym = 'a' and ms = time '00:01'", 1);
        assertTrue(lastQuery.contains(" sym = "));
    }

    @Test
    public void testIsNullQuery() {
        query("select count(*) from \"([] name: ``a`b)\" where name is null", 1);
        assertEquals(1, (long) res.getOnlyValue());
        assertTrue(lastQuery.contains("where null name"));

        query("select count(*) from \"([] name: ``a`b)\" where name is not null", 1);
        assertEquals(2, (long) res.getOnlyValue());
        assertTrue(lastQuery.contains("where not null name"));

        query("select count(*) from atable where iq is null", 1);
        assertEquals(0, (long) res.getOnlyValue());

        // String handling

        query("select count(*) from btable where strings is null",1);
        assertEquals(0, (long) res.getOnlyValue());

        query("select count(*) from btable where strings is not null",1);
        assertEquals(3, (long) res.getOnlyValue());
    }

    private static String lastQuery = null;
    private MaterializedResult res;
    private static Logger LOGGER = Logger.getLogger(TestKDBPlugin.class.getName());

    private void query(String sql, int expected) {
        res = computeActual(sql);
        if (res.getRowCount() < 100) {
            LOGGER.info("Query results: " + res);
        } else {
            LOGGER.info("Query results: " + res.getRowCount() + " rows");
        }
        assertEquals(res.getRowCount(), expected);
    }

    private void query(Session session, String sql, int expected) {
        res = computeActual(session, sql);
        if (res.getRowCount() < 100) {
            LOGGER.info("Query results: " + res);
        } else {
            LOGGER.info("Query results: " + res.getRowCount() + " rows");
        }
        assertEquals(res.getRowCount(), expected);
    }

    private void assertLastQuery(String ksql) {
        assertEquals(lastQuery, ksql);
    }

    private void assertResultColumn(int idx, Set expected) {
        assertEquals(res.getMaterializedRows().stream().map(row -> row.getField(idx)).collect(Collectors.toSet()), expected);
    }

    @Override
    protected QueryRunner createQueryRunner() throws Exception {
        DistributedQueryRunner qrunner = DistributedQueryRunner.builder(createSession("default"))
                .setNodeCount(1)
                .setExtraProperties(ImmutableMap.of())
                .build();

        qrunner.installPlugin(new KDBPlugin());
        qrunner.createCatalog("kdb", "kdb",
                ImmutableMap.<String,String>builder()
                        .put("kdb.host", "localhost")
                        .put("kdb.port", "8000")
                        .put("kdb.user", "user")
                        .put("kdb.password", "password")
                        .build());

        return qrunner;
    }

    private static Session createSession(String schema)
    {
        SessionPropertyManager sessionPropertyManager = new SessionPropertyManager();
        return testSessionBuilder(sessionPropertyManager)
                .setCatalog("kdb")
                .setSchema(schema)
                .build();
    }

}

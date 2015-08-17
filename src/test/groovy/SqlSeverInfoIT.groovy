import io.dbmaster.testng.BaseToolTestNGCase;

import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test
//import static org.hamcrest.CoreMatchers.containsString
//import static org.hamcrest.MatcherAssert.assertThat


import com.branegy.tools.api.ExportType;


public class SqlSeverInfoIT extends BaseToolTestNGCase {

    @Test
    public void test() {
        def parameters = [ "p_servers"  :  getTestProperty("sqlserver-server-info.p_database")]
        String result = tools.toolExecutor("sqlserver-server-info", parameters).execute()
        assertTrue(result.contains("Server"), "Unexpected search results ${result}");
        assertTrue(result.contains("Environment"), "Unexpected search results ${result}");
    }
}

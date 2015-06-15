import java.util.ArrayList
import java.util.Iterator
import java.util.List
import java.util.Map.Entry
import com.branegy.service.base.api.ProjectService
import com.branegy.service.core.QueryRequest
import com.branegy.service.connection.api.ConnectionService
import com.branegy.dbmaster.connection.ConnectionProvider
import com.branegy.dbmaster.connection.JdbcConnector
import groovy.sql.Sql
// TODO - read this http://sqlblog.com/blogs/kalen_delaney/archive/2007/12/08/hyperthreaded-or-not.aspx
// TODO  EXEC    sys.xp_readerrorlog 0, 1, 'System Manufacturer', '...'
def toURL = { link ->
    link==null ? "NULL" : link.encodeURL().replaceAll("\\+", "%20")
}
String.metaClass.encodeURL = { java.net.URLEncoder.encode(delegate) }

String projectName =  dbm.getService(ProjectService.class).getCurrentProject().getName()

// -----------------  Connections ---------------------------------------------------------------------------
connectionSrv = dbm.getService(ConnectionService.class)

def dbConnections
if (p_servers!=null && p_servers.size()>0) {
    dbConnections = p_servers.collect { serverName -> connectionSrv.findByName(serverName) }
} else {
    dbConnections = connectionSrv.getConnectionList()
}

// TODO Add information about patches and highlight most recent version

println  """
   <table class="simple-table" cellspacing="0" cellpadding="10">
        <tr style="background-color:#EEE">
            <td>Server</td>
            <td>Environment</td>
            <td>Notes</td>
            <td>OS</td>

            <td>IsClustered</td>
            <td>ComputerNamePhysicalNetBIOS</td>
            <td>Edition</td>
            <td>ProductLevel</td>
            <td>ProductVersion</td>
            <td>Collation</td>
            <td>IsFullTextInstalled</td>
            <td>IsIntegratedSecurityOnly</td>
            
            <td>CPU</td>
            <td>HT Ratio</td>
            <td>Physical CPU</td>
            <td>Memory [MB]</td>
            <td>Processor</td>
        </tr>
"""

def convertVersion = { serverVersion ->
    switch (serverVersion) {
        // TODO Azure ? 
        case "8.00": return  "SQL 2000"
        case "9.00": return  "SQL 2005"
        case "10.0": return  "SQL 2008"
        case "10.5": return  "SQL 2008 R2"
        case "10.50": return  "SQL 2008 R2"
        case "11.0": return  "SQL 2012"
        case "12.0": return  "SQL 2014"
        case "13.0": return  "SQL 2016"
        default: return serverVersion
    }
}

dbConnections.each { connectionInfo ->
    try {
        connector = ConnectionProvider.getConnector(connectionInfo)
        def serverName = connectionInfo.getName()
        if (!(connector instanceof JdbcConnector)) {
            logger.info("Skipping checks for connection ${connectionInfo.getName()} as it is not a database one")
            return
        }

        logger.info("Loading info for server ${serverName}");

        def dialect = connector.connect()

        connection = connector.getJdbcConnection(null)
        connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_UNCOMMITTED)
        def sql = new Sql(connection)

        query = """EXEC master.dbo.xp_regread  @rootkey = N'HKEY_LOCAL_MACHINE', 
                                               @key = N'SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion', 
                                               @value_name = N'ProductName'"""

        def osName = sql.firstRow(query).Data
        
        println "<tr><td>${serverName}</td>"
        def environment = connectionInfo.getCustomData("Environment") ?: ""
        def notes= connectionInfo.getCustomData("Notes") ?: ""
        println "<td>${environment}</td>"
        println "<td>${notes}</td>"


        def row = sql.firstRow("SELECT @@VERSION as version")

        println "<td>${osName}</td>"
        //<br/>${row.version.split("\\son\\s")[1]}</td>"


        logger.info("osName=${osName}");


        def query = """
            SELECT SERVERPROPERTY('IsClustered') AS [IsClustered], 
            SERVERPROPERTY('ComputerNamePhysicalNetBIOS') AS [ComputerNamePhysicalNetBIOS], 
            SERVERPROPERTY('Edition') AS [Edition], SERVERPROPERTY('ProductLevel') AS [ProductLevel], 
            SERVERPROPERTY('ProductVersion') AS [ProductVersion],
            SERVERPROPERTY('Collation') AS [Collation], SERVERPROPERTY('IsFullTextInstalled') AS [IsFullTextInstalled], 
            SERVERPROPERTY('IsIntegratedSecurityOnly') AS [IsIntegratedSecurityOnly]"""

        row = sql.firstRow(query)


        def productVersion = row.ProductVersion.split("\\.")[0]+"."+row.ProductVersion.split("\\.")[1]
        logger.info("Ver=${productVersion}");
       

        // TODO Highlight computer name if it is different
        println """ <td>${row.IsClustered}</td><td>${row.ComputerNamePhysicalNetBIOS}</td> 
                    <td>${row.Edition}</td><td>${row.ProductLevel}</td>
                    <td>${row.ProductVersion} (${convertVersion(productVersion)}) </td>
                    <td>${row.Collation}</td>
                    <td>${row.IsFullTextInstalled}</td><td>${row.IsIntegratedSecurityOnly}</td>"""

        // http://www.pythian.com/blog/how-to-collect-cluster-information-using-tsql/


        // def productVersion = row.ProductVersion.split("\\.")[0]+"."+row.ProductVersion.split("\\.")[1]


    // TODO FOR DETAILS -- Returns a list of all global trace flags that are enabled (Query 4) (Global Trace Flags)
    // DBCC TRACESTATUS (-1);
            /*
       switch (dialect.getDialectName().toLowerCase()){
       case "oracle":
           return  "select count(*) from \"${viewName}\"" // where ROWNUM <= ${max_rows}
       case "sqlserver":
           return  "select top ${max_rows} * from [${view.schema}].[${view.simpleName}] with (NOLOCK)"
       case "mysql":
           return  "select * from ${viewName} limit 0,${max_rows}"
       default:
           return  "select * from ${viewName}"
       } */

        // (Cannot distinguish between HT and multi-core)
        if (productVersion.toDouble()<11) {
            query = """  SELECT cpu_count, 
                                hyperthread_ratio,
                                cpu_count/hyperthread_ratio AS physical_cpu_count, 
                                physical_memory_in_bytes/1048576 AS [memory]
                           FROM sys.dm_os_sys_info WITH (NOLOCK) OPTION (RECOMPILE)"""
        } else {
            query = """  SELECT cpu_count, 
                                hyperthread_ratio,
                                cpu_count/hyperthread_ratio AS physical_cpu_count, 
                                physical_memory_kb/1024 AS [memory]
                           FROM sys.dm_os_sys_info WITH (NOLOCK) OPTION (RECOMPILE)"""
        }
        row = sql.firstRow(query)
        println """ <td>${row.cpu_count}</td>
                    <td>${row.hyperthread_ratio}</td>
                    <td>${row.physical_cpu_count}</td>
                    <td>${row.memory}</td>"""
        query = """EXEC xp_instance_regread N'HKEY_LOCAL_MACHINE', N'HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0', N'ProcessorNameString'; """

        row = sql.firstRow(query)
        println """    <td>${row.Data}</td>"""

    // TODO FOR DETAILS
    // SELECT name, value, value_in_use, [description] 
    // FROM sys.configurations WITH (NOLOCK)
    // ORDER BY name  OPTION (RECOMPILE);
        println "</tr>"

        dbm.closeResourceOnExit(connection)

    } catch (Exception e) {
        def msg = "Error occured "+e.getMessage()
        org.slf4j.LoggerFactory.getLogger(this.getClass()).error(msg,e);
        logger.error (msg)
    }
}
println "</table>"

logger.info("Check is completed")
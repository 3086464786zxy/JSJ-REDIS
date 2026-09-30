package db.migration;
import org.flywaydb.core.api.migration.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
/** Existing menu definitions and grants are preserved. */
public class V3__initial_permissions extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        try (var statement = context.getConnection().createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_menu")) {
            rows.next();
            if (rows.getLong(1)==0)
                ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource("db/initial-permissions.sql"));
        }
    }
    @Override public Integer getChecksum() { return 1; }
}

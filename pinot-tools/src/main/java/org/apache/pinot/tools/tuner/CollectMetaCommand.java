package org.apache.pinot.tools.tuner;

import java.util.Arrays;
import java.util.HashSet;
import org.apache.pinot.tools.AbstractBaseCommand;
import org.apache.pinot.tools.Command;
import org.apache.pinot.tools.tuner.driver.TunerDriver;
import org.apache.pinot.tools.tuner.meta.manager.collector.AccumulateStats;
import org.apache.pinot.tools.tuner.meta.manager.collector.CompressedFilePathIter;
import org.kohsuke.args4j.Option;


public class CollectMetaCommand extends AbstractBaseCommand implements Command {
  @Option(name = "-workDir", required = true, metaVar = "<String>", usage = "The directory to work on, for temporary files and output metadata.json file，must have r/w access")
  private String _workDir;

  @Option(name = "-segmentsDir", required = true, metaVar = "<String>", usage = "The directory, which contains tableNames/{tarred segments}")
  private String _segmentsDir;

  @Option(name = "-tables", required = false, usage = "Comma separated list of table names to work on without type (leave this blank to run on all tables)")
  private String _tableNamesWithoutType = null;

  @Override
  public boolean execute()
      throws Exception {
    HashSet<String> tableNamesWithoutType = null;
    if (_tableNamesWithoutType != null) {
      tableNamesWithoutType.addAll(Arrays.asList(_tableNamesWithoutType.split(",")));
    }

    TunerDriver metaFetch = new TunerTest().setThreadPoolSize(3).setStrategy(
        new AccumulateStats.Builder().setTableNamesWithoutType(tableNamesWithoutType).setOutputDir(_workDir).build())
        .setQuerySrc(new CompressedFilePathIter.Builder().set_directory(_segmentsDir).build()).setMetaManager(null);
    metaFetch.execute();
    return true;
  }

  @Override
  public String description() {
    return null;
  }

  @Override
  public boolean getHelp() {
    return false;
  }
}

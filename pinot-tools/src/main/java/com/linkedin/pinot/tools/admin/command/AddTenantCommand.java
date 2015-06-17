/**
 * Copyright (C) 2014-2015 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.pinot.tools.admin.command;

import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linkedin.pinot.common.config.Tenant;
import com.linkedin.pinot.common.utils.TenantRole;
import com.linkedin.pinot.controller.helix.ControllerRequestURLBuilder;


public class AddTenantCommand extends AbstractBaseCommand implements Command {
  private static final Logger _logger = LoggerFactory.getLogger(AddTenantCommand.class);

  @Option(name="-controllerPort", required=false, metaVar="<string>", usage="Port number to start the controller at.")
  private String _controllerPort = DEFAULT_CONTROLLER_PORT;

  @Option(name = "-name", required = true, metaVar = "<string>", usage = "Name of the tenant to be created")
  private String _name;

  @Option(name = "-role", required = true, metaVar = "<BROKER/SERVER>", usage = "Tenant role (broker/server).")
  private TenantRole _role;

  @Option(name = "-instanceCount", required = true, metaVar = "<int>", usage = "Number of instances.")
  private int _instanceCount;

  @Option(name = "-offlineInstanceCount", required = true, metaVar = "<int>", usage = "Number of offline instances.")
  private int _offlineInstanceCount;

  @Option(name = "-realTimeInstanceCount", required = true, metaVar = "<int>", usage = "Number of realtime instances.")
  private int _realtimeInstanceCount;

  @Option(name = "-help", required = false, help = true, aliases={"-h", "--h", "--help"}, usage = "Print this message.")
  private boolean _help = false;

  private String _controllerAddress = "http://localhost:" + _controllerPort;

  public AddTenantCommand setControllerUrl(String url) {
    _controllerAddress = url;
    return this;
  }

  public AddTenantCommand setName(String name) {
    _name = name;
    return this;
  }

  public AddTenantCommand setRole(TenantRole role) {
    _role = role;
    return this;
  }

  public AddTenantCommand setInstances(int instances) {
    _instanceCount = instances;
    return this;
  }

  public AddTenantCommand setOffline(int offline) {
    _offlineInstanceCount = offline;
    return this;
  }

  public AddTenantCommand setRealtime(int realtime) {
    _realtimeInstanceCount = realtime;
    return this;
  }

  @Override
  public boolean execute() throws Exception {
    Tenant t =
        new Tenant.TenantBuilder(_name).setRole(_role).setTotalInstances(_instanceCount).setOfflineInstances(_offlineInstanceCount)
            .setRealtimeInstances(_realtimeInstanceCount).build();

    String res =
        AbstractBaseCommand.sendPostRequest(ControllerRequestURLBuilder.baseUrl(_controllerAddress).forTenantCreate(), t.toString());

    _logger.info(res);
    System.out.print(res);
    return true;
  }

  @Override
  public String getName() {
    return "AddTenant";
  }

  @Override
  public boolean getHelp() {
    return _help;
  }

  @Override
  public String toString() {
    return ("AddTenant -controllerAddress " + _controllerAddress + " -name " + _name +
        " -role " + _role + " -instanceCount " + _instanceCount + " -offlineInstanceCount " +
        _offlineInstanceCount + " -realTimeInstanceCount " + _realtimeInstanceCount);
  }

  @Override
  public String description() {
    return "Add tenant as per the specification provided.";
  }
}

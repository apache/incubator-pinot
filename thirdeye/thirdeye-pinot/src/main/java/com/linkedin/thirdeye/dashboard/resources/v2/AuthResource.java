package com.linkedin.thirdeye.dashboard.resources.v2;

import com.linkedin.thirdeye.auth.AuthRequest;
import com.linkedin.thirdeye.auth.IAuthManager;
import com.linkedin.thirdeye.auth.PrincipalAuthContext;
import com.linkedin.thirdeye.datasource.DAORegistry;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
  public static final String AUTH_TOKEN_NAME = "te_auth";
  private static final Logger LOG = LoggerFactory.getLogger(AuthResource.class);
  private final IAuthManager authManager;

  public AuthResource() {
    authManager = DAORegistry.getInstance().getAuthManager();
  }


  @Path("/authenticate")
  @POST
  public Response authenticate(AuthRequest authRequest) {
    try {
      PrincipalAuthContext authContext = authManager.authenticate(authRequest.getPrincipal(), authRequest.getPrincipal());
      NewCookie cookie = new NewCookie(AUTH_TOKEN_NAME, authManager.buildAuthToken(authContext));
      return Response.ok(authContext).cookie(cookie).build();
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    }
    return Response.status(Response.Status.UNAUTHORIZED).build();
  }

  /**
   * If there was a valid token, the request interceptor would have set PrincipalContext already.
   * @return
   */
  @GET
  public Response getPrincipalContext() {
    PrincipalAuthContext authContext = authManager.getCurrentPrincipal();
    if (authContext == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    return Response.ok(authContext).build();
  }
}
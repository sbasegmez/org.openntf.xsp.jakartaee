/**
 * Copyright (c) 2018-2023 Contributors to the XPages Jakarta EE Support Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openntf.xsp.jaxrs.exceptions;

import java.lang.reflect.Method;

import org.openntf.xsp.jakartaee.util.LibraryUtil;
import org.openntf.xsp.jaxrs.exceptions.handler.JsonExceptionHandler;
import org.openntf.xsp.jaxrs.exceptions.handler.RestExceptionHandler;

import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;

/**
 * @author Jesse Gallagher
 * @since 2.3.0
 */
@Priority(Priorities.USER+2)
public class GenericThrowableMapper implements ExceptionMapper<Throwable> {

	@Context
	private UriInfo uriInfo;
	
	@Context
	private HttpServletRequest req;
	
	@Context
	private ResourceInfo resourceInfo;
	
	@Context
	private Request request;

	@Override
	public Response toResponse(final Throwable t) {
		// Depending on the container, this may be called for exceptions better handled by more-specialized classes
		if(t instanceof NotFoundException) {
			return NotFoundMapper.INSTANCE.toResponse((NotFoundException)t, request, uriInfo);
		}

		if (t instanceof WebApplicationException) {
			WebApplicationException e = (WebApplicationException) t;
			if (e.getResponse() != null) {
				return e.getResponse();
			} else {
				return createResponseFromException(t, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, resourceInfo, req);
			}
		} else {
			return createResponseFromException(t, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, resourceInfo, req);
		}
	}
	
	protected MediaType getMediaType(ResourceInfo resourceInfo) {
		if(resourceInfo == null) {
			return MediaType.APPLICATION_JSON_TYPE;
		}
		
		Method method = resourceInfo.getResourceMethod();
		if(method == null) {
			// Shows up currently with MVC, which breaks resolution
			return MediaType.APPLICATION_JSON_TYPE;
		}
		Produces produces = method.getAnnotation(Produces.class);
		if(produces != null) {
			// Assume the first is "true"
			return MediaType.valueOf(produces.value()[0]);
		} else {
			return MediaType.APPLICATION_JSON_TYPE;
		}
	}

	protected Response createResponseFromException(final Throwable throwable, final int status, ResourceInfo resourceInfo, HttpServletRequest req) {
		MediaType type = getMediaType(resourceInfo);
		RestExceptionHandler handler = LibraryUtil.findExtensionsSorted(RestExceptionHandler.class, false)
			.stream()
			.filter(h -> h.canHandle(resourceInfo, type))
			.findFirst()
			.orElse(JsonExceptionHandler.DEFAULT);
		return handler.handle(throwable, status, resourceInfo, req);
	}

}

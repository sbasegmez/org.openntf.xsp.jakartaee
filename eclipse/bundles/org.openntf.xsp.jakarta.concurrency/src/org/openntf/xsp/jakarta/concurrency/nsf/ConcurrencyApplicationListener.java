package org.openntf.xsp.jakarta.concurrency.nsf;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService.RejectPolicy;
import org.glassfish.enterprise.concurrent.ContextServiceImpl;
import org.glassfish.enterprise.concurrent.ManagedExecutorServiceImpl;
import org.glassfish.enterprise.concurrent.ManagedScheduledExecutorServiceImpl;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.openntf.xsp.jakarta.concurrency.DominoContextSetupProvider;
import org.openntf.xsp.jakarta.concurrency.NotesManagedThreadFactory;
import org.openntf.xsp.jakarta.concurrency.servlet.ConcurrencyRequestListener;
import org.openntf.xsp.jakartaee.servlet.ServletUtil;

import com.ibm.domino.xsp.module.nsf.NSFComponentModule;
import com.ibm.domino.xsp.module.nsf.NotesContext;
import com.ibm.xsp.application.ApplicationEx;
import com.ibm.xsp.application.events.ApplicationListener2;

import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.servlet.ServletContext;

/**
 * Listens for XPages application initialization - which should be roughly
 * comparable to ServletContext lifecycle - to attach a listener to init/term
 * JNDI configuration for Concurrency.
 * 
 * @author Jesse Gallagher
 * @since 2.7.0
 */
public class ConcurrencyApplicationListener implements ApplicationListener2 {
	private static final Logger log = Logger.getLogger(ConcurrencyApplicationListener.class.getPackage().getName());

	public static final String ATTR_THREADFACTORY = ConcurrencyApplicationListener.class.getName() + "_threadFactory"; //$NON-NLS-1$

	@Override
	public void applicationCreated(ApplicationEx app) {
		
		getServletContext().ifPresent(ctx -> {
			ctx.addListener(new ConcurrencyRequestListener());
			
			ContextSetupProvider provider = new DominoContextSetupProvider();
			
			ContextServiceImpl contextService = new ContextServiceImpl("contextService" + app.getApplicationId(), provider); //$NON-NLS-1$
			NotesManagedThreadFactory factory = new NotesManagedThreadFactory("fac" + app.getApplicationId(), contextService); //$NON-NLS-1$
			ctx.setAttribute(ATTR_THREADFACTORY, factory);
			
			ManagedExecutorService exec = new ManagedExecutorServiceImpl(
				"executor" + app.getApplicationId(), //$NON-NLS-1$
				factory,
				0,
				true,
				5,
				10,
				30,
				TimeUnit.MINUTES,
				TimeUnit.MINUTES.toSeconds(30),
				0,
				contextService,
				RejectPolicy.ABORT
			);
			ctx.setAttribute(ConcurrencyRequestListener.ATTR_EXECUTORSERVICE, exec);
			
			ManagedScheduledExecutorService scheduledExec = new ManagedScheduledExecutorServiceImpl(
				"scheduledExecutor" + app.getApplicationId(), //$NON-NLS-1$
				factory,
				0,
				true,
				5,
				30,
				TimeUnit.MINUTES,
				TimeUnit.MINUTES.toSeconds(30),
				contextService,
				RejectPolicy.ABORT
			);
			ctx.setAttribute(ConcurrencyRequestListener.ATTR_SCHEDULEDEXECUTORSERVICE, scheduledExec);
		});
	}

	@Override
	public void applicationDestroyed(ApplicationEx app) {
		getServletContext().ifPresent(ctx -> {
			ManagedExecutorService exec = (ManagedExecutorService)ctx.getAttribute(ConcurrencyRequestListener.ATTR_EXECUTORSERVICE);
			if(exec != null) {
				try {
					exec.shutdownNow();
					exec.awaitTermination(5, TimeUnit.MINUTES);
				} catch (Exception e) {
					e.printStackTrace();
					if(log.isLoggable(Level.SEVERE)) {
						log.log(Level.SEVERE, "Encountered exception terminating executor service", e);
					}
				}
			}
			
			ManagedScheduledExecutorService scheduledExec = (ManagedScheduledExecutorService)ctx.getAttribute(ConcurrencyRequestListener.ATTR_SCHEDULEDEXECUTORSERVICE);
			if(scheduledExec != null) {
				try {
					scheduledExec.shutdownNow();
					scheduledExec.awaitTermination(5, TimeUnit.MINUTES);
				} catch (Exception e) {
					e.printStackTrace();
					if(log.isLoggable(Level.SEVERE)) {
						log.log(Level.SEVERE, "Encountered exception terminating scheduled executor service", e);
					}
				}
			}
			
			NotesManagedThreadFactory fac = (NotesManagedThreadFactory)ctx.getAttribute(ATTR_THREADFACTORY);
			if(fac != null) {
				fac.stop();
			}
		});
	}

	@Override
	public void applicationRefreshed(ApplicationEx app) {
		
	}
	
	private Optional<NSFComponentModule> getModule() {
		NotesContext ctx = NotesContext.getCurrentUnchecked();
		if(ctx != null) {
			return Optional.ofNullable(ctx.getModule());
		}
		return Optional.empty();
	}

	private Optional<ServletContext> getServletContext() {
		return getModule()
			.map(module -> {
				javax.servlet.ServletContext oldContext = module.getServletContext();
				ServletContext servletContext = ServletUtil.oldToNew(module.getDatabasePath(), oldContext);
				return servletContext;
			});
	}
}

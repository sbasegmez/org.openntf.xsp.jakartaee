package org.openntf.xsp.jakarta.concurrency;

import java.util.Map;

import org.glassfish.enterprise.concurrent.spi.ContextHandle;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;

/**
 * This extension interface can be used to declare participants in events
 * for managed executor services.
 * 
 * @author Jesse Gallagher
 * @since 2.7.0
 */
public interface ContextSetupParticipant {
	String EXTENSION_ID = ContextSetupParticipant.class.getName();
	
	/**
     * Called by ManagedExecutorService in the same thread that submits a
     * task to save the execution context of the submitting thread. 
     * 
     * @param contextHandle ContextHandle created by the primary provider
     */
    public void saveContext(ContextHandle contextHandle);
    
    /**
     * Called by ManagedExecutorService in the same thread that submits a
     * task to save the execution context of the submitting thread. 
     * 
     * @param contextService ContextService containing information on what
     * context should be saved
     * @param contextObjectProperties Additional properties specified for
     * for a context object when the ContextService object was created.
     * 
     * @return A ContextHandle that will be passed to the setup method
     * in the thread executing the task
     */
    default public void saveContext(ContextHandle contextHandle, Map<String, String> contextObjectProperties) {
    	
    }
	
	/**
     * Called by ManagedExecutorService before executing a task to set up thread
     * context. It will be called in the thread that will be used for executing
     * the task.
     * 
     * @param contextHandle The ContextHandle object obtained from the call
     * to {@link ContextSetupProvider#saveContext}
     * 
     * @throws IllegalStateException if the ContextHandle is no longer valid.
     * For example, the application component that the ContextHandle was 
     * created for is no longer running or is undeployed.
     */
    public void setup(ContextHandle contextHandle) throws IllegalStateException;
    
    /**
     * Called by ManagedExecutorService after executing a task to clean up and
     * reset thread context. It will be called in the thread that was used
     * for executing the task.
     * 
     * @param contextHandle The ContextHandle object obtained from the call
     * to #setup
     */
    public void reset(ContextHandle contextHandle);
}

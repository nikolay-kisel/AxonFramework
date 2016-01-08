/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.messaging.unitofwork;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.metadata.CorrelationDataProvider;
import org.axonframework.messaging.metadata.MetaData;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class represents a Unit of Work that monitors the processing of a {@link Message}.
 * <p/>
 * Before processing begins a Unit of Work is bound to the active thread by registering it with the
 * {@link CurrentUnitOfWork}. After processing, the Unit of Work is unregistered from the {@link CurrentUnitOfWork}.
 * <p/>
 * Handlers can be notified about the state of the processing of the Message by registering with this Unit of Work.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface UnitOfWork {

    /**
     * Starts the current unit of work. The UnitOfWork instance is registered with the CurrentUnitOfWork.
     */
    void start();

    /**
     * Commits the Unit of Work. This should be invoked after the Unit of Work Message has been processed. Handlers
     * registered to the Unit of Work will be notified.
     * <p/>
     * After the commit (successful or not), any registered clean-up handlers ({@link #onCleanup(Consumer)}}) will
     * be invoked and the Unit of Work is unregistered from the {@link CurrentUnitOfWork}.
     * <p/>
     * If the Unit of Work fails to commit, e.g. because an exception is raised by one of its handlers, the Unit of
     * Work is rolled back.
     *
     * @throws IllegalStateException if the UnitOfWork wasn't started or if the Unit of Work is not the 'current'
     * Unit of Work returned by {@link CurrentUnitOfWork#get()}.
     */
    void commit();

    /**
     * Initiates the rollback of this Unit of Work, invoking all registered rollback ({@link #onRollback(BiConsumer)
     * and clean-up handlers {@link #onCleanup(Consumer)}} respectively. Finally, the Unit of Work is unregistered
     * from the {@link CurrentUnitOfWork}.
     * <p/>
     * If the rollback is a result of an exception, consider using {@link #rollback(Throwable)} instead.
     *
     * @throws IllegalStateException if the Unit of Work is not in a compatible phase.
     */
    default void rollback() {
        rollback(null);
    }

    /**
     * Initiates the rollback of this Unit of Work, invoking all registered rollback ({@link #onRollback(BiConsumer)
     * and clean-up handlers {@link #onCleanup(Consumer)}} respectively. Finally, the Unit of Work is unregistered
     * from the {@link CurrentUnitOfWork}.
     *
     * @param cause The cause of the rollback. May be <code>null</code>.
     * @throws IllegalStateException if the Unit of Work is not in a compatible phase.
     */
    void rollback(Throwable cause);

    /**
     * Indicates whether this UnitOfWork is started. It is started when the {@link #start()} method has been called,
     * and if the UnitOfWork has not been committed or rolled back.
     *
     * @return <code>true</code> if this UnitOfWork is started, <code>false</code> otherwise.
     */
    default boolean isActive() {
        return phase().isStarted();
    }

    /**
     * Returns the current phase of the Unit of Work.
     *
     * @return the Unit of Work phase
     */
    Phase phase();

    /**
     * Register given <code>handler</code> with the Unit of Work. The handler will be notified when the phase of
     * the Unit of Work changes to {@link Phase#PREPARE_COMMIT}.
     *
     * @param handler the handler to register with the Unit of Work
     */
    void onPrepareCommit(Consumer<UnitOfWork> handler);

    /**
     * Register given <code>handler</code> with the Unit of Work. The handler will be notified when the phase of
     * the Unit of Work changes to {@link Phase#COMMIT}.
     *
     * @param handler the handler to register with the Unit of Work
     */
    void onCommit(Consumer<UnitOfWork> handler);

    /**
     * Register given <code>handler</code> with the Unit of Work. The handler will be notified when the phase of
     * the Unit of Work changes to {@link Phase#AFTER_COMMIT}.
     *
     * @param handler the handler to register with the Unit of Work
     */
    void afterCommit(Consumer<UnitOfWork> handler);

    /**
     * Register given <code>handler</code> with the Unit of Work. The handler will be notified when the phase of
     * the Unit of Work changes to {@link Phase#ROLLBACK}.
     *
     * @param handler the handler to register with the Unit of Work
     */
    void onRollback(BiConsumer<UnitOfWork, Throwable> handler);

    /**
     * Register given <code>handler</code> with the Unit of Work. The handler will be notified when the phase of
     * the Unit of Work changes to {@link Phase#CLEANUP}.
     *
     * @param handler the handler to register with the Unit of Work
     */
    void onCleanup(Consumer<UnitOfWork> handler);

    /**
     * Returns an optional for the parent of this Unit of Work. The optional holds the Unit of Work that was active
     * when this Unit of Work was started. In case no other Unit of Work was active when this Unit of Work was started
     * the optional is empty, indicating that this is the Unit of Work root.
     *
     * @return an optional parent Unit of Work
     */
    Optional<UnitOfWork> parent();

    /**
     * Returns the root of this Unit of Work. If this Unit of Work has no parent (see {@link #parent()}) it returns
     * itself, otherwise it returns the root of its parent.
     *
     * @return the root of this Unit of Work
     */
    default UnitOfWork root() {
        return parent().map(UnitOfWork::root).orElse(this);
    }

    /**
     * Get the message that is being processed by the Unit of Work. A Unit of Work processes a single Message over its
     * life cycle.
     *
     * @return the Message being processed by this Unit of Work
     */
    Message<?> getMessage();

    /**
     * Get the correlation data contained in the {@link #getMessage() message} being processed by the Unit of Work.
     * <p/>
     * By default this correlation data will be copied to other {@link Message messages} created in the context of
     * this Unit of Work, so long as these messages extend from {@link org.axonframework.messaging.GenericMessage}.
     *
     * @return The correlation data contained in the message processed by this Unit of Work
     */
    MetaData getCorrelationData();

    /**
     * Register given <code>correlationDataProvider</code> with this Unit of Work. Correlation data providers are used
     * to provide meta data based on this Unit of Work's {@link #getMessage() Message} when
     * {@link #getCorrelationData()} is invoked.
     *
     * @param correlationDataProvider the Correlation Data Provider to register
     */
    void registerCorrelationDataProvider(CorrelationDataProvider correlationDataProvider);

    /**
     * Returns a mutable map of resources registered with the Unit of Work.
     *
     * @return mapping of resources registered with this Unit of Work
     */
    Map<String, Object> resources();

    /**
     * Returns the resource attached under given <code>name</code>, or <code>null</code> if no such resource
     * is available.
     *
     * @param name The name under which the resource was attached
     * @param <T>  The type of resource
     * @return The resource mapped to the given <code>name</code>, or <code>null</code> if no resource was found.
     */
    @SuppressWarnings("unchecked")
    default <T> T getResource(String name) {
        return (T) resources().get(name);
    }

    /**
     * Returns the resource attached under given <code>name</code>. If there is no resource mapped to the given key
     * yet the <code>mappingFunction</code> is invoked to provide the mapping.
     *
     * @param key  The name under which the resource was attached
     * @param <T>  The type of resource
     * @return The resource mapped to the given <code>key</code>, or the resource returned by the
     * <code>mappingFunction</code> if no resource was found.
     */
    @SuppressWarnings("unchecked")
    default <T> T getOrComputeResource(String key, Function<? super String, T> mappingFunction) {
        return (T) resources().computeIfAbsent(key, mappingFunction);
    }

    /**
     * Execute the given <code>task</code> in the context of this Unit of Work. If the Unit of Work is not started yet
     * it will be started.
     * <p/>
     * If the task executes successfully the Unit of Work is committed. If any exception is raised
     * while executing the task, the Unit of Work is rolled back and the exception is thrown.
     *
     * @param task the task to execute
     */
    default void execute(Runnable task) {
        execute(task, RollbackConfigurationType.ANY_THROWABLE);
    }

    /**
     * Execute the given <code>task</code> in the context of this Unit of Work. If the Unit of Work is not started yet
     * it will be started.
     * <p/>
     * If the task executes successfully the Unit of Work is committed. If an exception is raised
     * while executing the task, the <code>rollbackConfiguration</code> determines if the Unit of Work should be
     * rolled back or committed, and the exception is thrown.
     *
     * @param task                  the task to execute
     * @param rollbackConfiguration configuration that determines whether or not to rollback the unit of work when
     *                              task execution fails
     */
    void execute(Runnable task, RollbackConfiguration rollbackConfiguration);

    /**
     * Execute the given <code>task</code> in the context of this Unit of Work. If the Unit of Work is not started yet
     * it will be started.
     * <p/>
     * If the task executes successfully the Unit of Work is committed and the result of the task is returned. If any
     * exception is raised while executing the task, the Unit of Work is rolled back and the exception is thrown.
     *
     * @param <R>                   the type of result that is returned after successful execution
     * @param task                  the task to execute
     */
    default <R> R executeWithResult(Callable<R> task) throws Exception {
        return executeWithResult(task, RollbackConfigurationType.ANY_THROWABLE);
    }

    /**
     * Execute the given <code>task</code> in the context of this Unit of Work. If the Unit of Work is not started yet
     * it will be started.
     * <p/>
     * If the task executes successfully the Unit of Work is committed and the result of the task is returned. If
     * execution fails, the <code>rollbackConfiguration</code> determines if the Unit of Work should be rolled back
     * or committed.
     *
     * @param <R>                   the type of result that is returned after successful execution
     * @param task                  the task to execute
     * @param rollbackConfiguration configuration that determines whether or not to rollback the unit of work when
     *                              task execution fails
     */
    <R> R executeWithResult(Callable<R> task, RollbackConfiguration rollbackConfiguration) throws Exception;

    /**
     * Get the result of the task that was executed by this Unit of Work. If the Unit of Work has not been given a task
     * to execute this method returns <code>null</code>.
     *
     * @return The result of the task executed by this Unit of Work, or <code>null</code> if the Unit of Work has not
     * been given a task to execute.
     */
    ExecutionResult getExecutionResult();

    /**
     * Enum indicating possible phases of the Unit of Work.
     */
    enum Phase {

        /**
         * Indicates that the unit of work has been created but has not been registered with the
         * {@link CurrentUnitOfWork} yet.
         */
        NOT_STARTED(false, false),

        /**
         * Indicates that the Unit of Work has been registered with the {@link CurrentUnitOfWork} but has not been
         * committed, because its Message has not been processed yet.
         */
        STARTED(true, false),

        /**
         * Indicates that the Unit of Work is preparing its commit. This means that {@link #commit()} has been invoked
         * on the Unit of Work, indicating that the Message {@link #getMessage()} of the Unit of Work has been
         * processed.
         * <p/>
         * All handlers registered to be notified before commit {@link #onPrepareCommit} will be invoked. If no
         * exception is raised by any of the handlers the Unit of Work will go into the {@link #COMMIT} phase,
         * otherwise it will be rolled back.
         */
        PREPARE_COMMIT(true, false),

        /**
         * Indicates that the Unit of Work has been committed and is passed the {@link #PREPARE_COMMIT} phase.
         */
        COMMIT(true, false),

        /**
         * Indicates that the Unit of Work is being rolled back. Generally this is because an exception was raised
         * while processing the {@link #getMessage() message} or while the Unit of Work was being committed.
         */
        ROLLBACK(true, true),

        /**
         * Indicates that the Unit of Work is after a successful commit. In this phase the Unit of Work cannot be
         * rolled back anymore.
         */
        AFTER_COMMIT(true, true),

        /**
         * Indicates that the Unit of Work is after a successful commit or after a rollback. Any resources tied to
         * this Unit of Work should be released.
         */
        CLEANUP(false, true),

        /**
         * Indicates that the Unit of Work is at the end of its life cycle. This phase is final.
         */
        CLOSED(false, true);

        private final boolean started;
        private final boolean reverseCallbackOrder;

        Phase(boolean started, boolean reverseCallbackOrder) {
            this.started = started;
            this.reverseCallbackOrder = reverseCallbackOrder;
        }

        /**
         * Check if a Unit of Work in this phase has been started, i.e. is registered with the
         * {@link CurrentUnitOfWork}.
         *
         * @return <code>true</code> if the Unit of Work is started when in this phase, <code>false</code> otherwise
         */
        public boolean isStarted() {
            return started;
        }

        /**
         * Check whether registered handlers for this phase should be invoked in the order of registration (first
         * registered handler is invoked first) or in the reverse order of registration (last registered handler is
         * invoked first).
         *
         * @return <code>true</code> if the order of invoking handlers in this phase should be in the reverse order
         * of registration, <code>false</code> otherwise.
         */
        public boolean isReverseCallbackOrder() {
            return reverseCallbackOrder;
        }

        /**
         * Check if this Phase comes before given other <code>phase</code>.
         *
         * @param phase The other Phase
         * @return <code>true</code> if this comes before the given <code>phase</code>, <code>false</code> otherwise.
         */
        public boolean isBefore(Phase phase) {
            return ordinal() < phase.ordinal();
        }

        /**
         * Check if this Phase comes after given other <code>phase</code>.
         *
         * @param phase The other Phase
         * @return <code>true</code> if this comes after the given <code>phase</code>, <code>false</code> otherwise.
         */
        public boolean isAfter(Phase phase) {
            return ordinal() > phase.ordinal();
        }
    }
}
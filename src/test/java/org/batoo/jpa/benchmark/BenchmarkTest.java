/*
 * Copyright (c) 2012 - Batoo Software ve Consultancy Ltd.
 * 
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.batoo.jpa.benchmark;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Root;

import org.apache.commons.lang.mutable.MutableLong;
import org.batoo.common.log.BLogger;
import org.batoo.common.log.BLoggerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * @author hceylan
 * @since 2.0.0
 */
public class BenchmarkTest {

	/**
	 * The number of tests to run
	 */
	private static final int ITERATIONS = Integer.valueOf(BenchmarkTest.getProp("iterations", "100"));

	/**
	 * If the results should be summarized
	 */
	private static final boolean SUMMARIZE = true;

	/**
	 * the number of worker thread to simulate concurrency
	 */
	private static final int THREAD_COUNT = Integer.valueOf(BenchmarkTest.getProp("threads", Integer.toString(Runtime.getRuntime().availableProcessors() / 2)));

	/**
	 * the number of worker thread to simulate concurrency
	 */
	private static final String DB = BenchmarkTest.getProp("db", "h2");

	/**
	 * The max test time, the default is 30 mins.
	 */
	private static final long MAX_TEST_TIME = Integer.valueOf(BenchmarkTest.getProp("maxTime", Integer.toString(30 * 60 * 1000)));

	/**
	 * The sample rate to collect data in microseconds
	 */
	private static final int SAMPLE_RATE = 10 ^ Integer.valueOf(BenchmarkTest.getProp("sampling", Integer.toString(2)));;

	private static final boolean FULL_SUMMARY = BenchmarkTest.getProp("fullSummary", null) != null;

	private static final BLogger LOG = BLoggerFactory.getLogger(BenchmarkTest.class);

	private static final String SEPARATOR = "________________________________________";
	private static final String LONG_SEPARATOR = "______________________________________________________";

	private static String getProp(String key, String defaultValue) {
		final String value = System.getProperty(key);

		return value != null ? value : defaultValue;
	}

	private Country country;
	private TimeElement element;
	private final HashMap<String, TimeElement> elements = Maps.newHashMap();
	private Type type;

	private boolean running;

	private long[] currentThreadTimes;

	private long[] threadIds;

	private int totalSamples;

	private long totalTime;

	private void close(final EntityManager em) {
		em.getTransaction().commit();

		em.close();
	}

	private ExecutorService createExecutor(BlockingQueue<Runnable> workQueue) {
		final AtomicInteger nextThreadNo = new AtomicInteger(0);

		final ThreadPoolExecutor pool = new ThreadPoolExecutor(//
			BenchmarkTest.THREAD_COUNT, BenchmarkTest.THREAD_COUNT, // min max threads
			0L, TimeUnit.MILLISECONDS, // the keep alive time - hold it forever
			workQueue, new ThreadFactory() {

				@Override
				public Thread newThread(Runnable r) {
					final Thread t = new Thread(r);
					t.setDaemon(true);
					t.setPriority(Thread.NORM_PRIORITY);

					BenchmarkTest.this.threadIds[nextThreadNo.getAndIncrement()] = t.getId();

					return t;
				}
			});

		pool.prestartAllCoreThreads();

		return pool;
	}

	private Person[][] createPersons() {
		final Person[][] persons = new Person[10][10];

		for (int i = 0; i < 10; i++) {
			for (int j = 0; j < 10; j++) {
				final Person person = new Person();

				person.setName("Hasan");

				final Address address = new Address();
				address.setCity("Istanbul");
				address.setPerson(person);
				address.setCountry(this.country);
				person.getAddresses().add(address);

				final Address address2 = new Address();
				address2.setCity("Istanbul");
				address2.setPerson(person);
				address2.setCountry(this.country);
				person.getAddresses().add(address2);

				final Phone phone = new Phone();
				phone.setPhoneNo("111 222-3344");
				phone.setPerson(person);
				person.getPhones().add(phone);

				final Phone phone2 = new Phone();
				phone2.setPhoneNo("111 222-3344");
				phone2.setPerson(person);
				person.getPhones().add(phone2);

				final Phone phone3 = new Phone();
				phone3.setPhoneNo("111 222-3344");
				phone3.setPerson(person);
				person.getPhones().add(phone3);

				persons[i][j] = person;
			}
		}

		return persons;
	}

	private void doBenchmarkCriteria(final EntityManagerFactory emf, Person[][] people, CriteriaQuery<Address> cq, ParameterExpression<Person> p) {
		for (int i = 0; i < (people.length / 2); i++) {
			for (final Person person : people[i]) {
				final EntityManager em = this.open(emf);

				final TypedQuery<Address> q = em.createQuery(cq);
				q.setParameter(p, person);
				q.getResultList();

				this.close(em);
			}
		}
	}

	private void doBenchmarkFind(final EntityManagerFactory emf, Person[][] people) {
		for (int i = 0; i < (people.length / 2); i++) {
			for (final Person person : people[i]) {
				final EntityManager em = this.open(emf);

				final Person person2 = em.find(Person.class, person.getId());
				person2.getPhones().size();

				this.close(em);
			}
		}
	}

	private void doBenchmarkJpql(final EntityManagerFactory emf, final Person[][] people) {
		for (int i = 0; i < (people.length / 2); i++) {
			for (final Person person : people[i]) {
				final EntityManager em = this.open(emf);

				final TypedQuery<Address> q = em.createQuery(
					"select a from Person p inner join p.addresses a left join fetch a.country left join fetch a.person where p = :person", Address.class);

				q.setParameter("person", person);
				q.getResultList();

				this.close(em);
			}
		}
	}

	private void doBenchmarkPersist(final EntityManagerFactory emf, Person[][] allPersons) {
		for (final Person[] persons : allPersons) {
			for (final Person person : persons) {
				final EntityManager em = this.open(emf);

				em.persist(person);

				this.close(em);
			}
		}
	}

	private void doBenchmarkRemove(final EntityManager em, final Person person) {
		em.remove(person);

		this.close(em);
	}

	private void doBenchmarkUpdate(final EntityManager em, final Person person2) {
		person2.setName("Hasan Ceylan");

		this.close(em);
	}

	private void doRemove(final EntityManagerFactory emf, Person[][] people) {
		for (int i = 0; i < (people.length / 2); i++) {
			for (Person person : people[i]) {
				final EntityManager em = this.open(emf);

				person = em.find(Person.class, person.getId());

				this.doBenchmarkRemove(em, person);
			}
		}
	}

	private void doUpdate(final EntityManagerFactory emf, final Person[][] people) {
		for (final Person[] persons : people) {
			for (final Person person : persons) {
				final EntityManager em = this.open(emf);

				this.doBenchmarkUpdate(em, em.find(Person.class, person.getId()));
			}
		}
	}

	private boolean isInDb(final StackTraceElement stElement) {
		return stElement.getClassName().startsWith("org.apache.derby") || stElement.getClassName().startsWith("com.mysql")
			|| stElement.getClassName().startsWith("org.h2") || stElement.getClassName().startsWith("org.hsqldb");
	}

	private synchronized boolean isRunning() {
		return this.running;
	}

	/**
	 * 
	 * @since 2.0.0
	 */
	@After
	public void measureAfter() {
		if (BenchmarkTest.SUMMARIZE) {

			if (BenchmarkTest.SUMMARIZE && !BenchmarkTest.FULL_SUMMARY) {
				System.err.println();
				System.err.println();
				System.err.println(BenchmarkTest.LONG_SEPARATOR);
				System.err.println(MessageFormat.format(
					"Provider: {0}, DB: {1}, Threads: {2}, Iterations: {3}\nTotal Time (msec): {4}, Samples Collected: {5}", this.type, // 0
					BenchmarkTest.DB, // 1
					BenchmarkTest.THREAD_COUNT, // 2
					BenchmarkTest.ITERATIONS, // 3
					this.totalTime, // 4
					this.totalSamples // 5
				));
				System.err.println(BenchmarkTest.LONG_SEPARATOR);
				System.err.println("Test Name\tDB Time \tJPA Time   ");
				System.err.println(BenchmarkTest.SEPARATOR);
			}

			final MutableLong dbTotalTime = new MutableLong(0);
			final MutableLong jpaTotalTime = new MutableLong(0);
			this.element.dump0(this.type, dbTotalTime, jpaTotalTime, BenchmarkTest.FULL_SUMMARY);

			if (!BenchmarkTest.FULL_SUMMARY) {
				System.err.println(BenchmarkTest.SEPARATOR);
				System.err.println(//
				"TOTAL " + this.type.name() + //
					" \t" + String.format("%08d", dbTotalTime.longValue()) + //
					" \t" + String.format("%08d", jpaTotalTime.longValue()));
				System.err.println(BenchmarkTest.LONG_SEPARATOR);
				System.err.println();
			}
			else {
				System.err.println();
				System.err.println();
			}
		}
		else {
			this.element.dump1(0, 0);

			System.err.println("\n");

			int rowNo = 0;
			final ArrayList<TimeElement> elements = Lists.newArrayList(this.elements.values());
			Collections.sort(elements, new Comparator<TimeElement>() {

				@Override
				public int compare(TimeElement o1, TimeElement o2) {
					return o1.getSelf().compareTo(o2.getSelf());
				}
			});

			for (final TimeElement element : elements) {
				rowNo++;
				element.dump2(rowNo);
			}

			System.err.println("\n");
		}
	}

	/**
	 * 
	 * @since 2.0.0
	 */
	@Before
	public void measureBefore() {
		final AtomicInteger nextThreadNo = new AtomicInteger();

		final BlockingQueue<Runnable> profilingQueue = new LinkedBlockingQueue<Runnable>();

		new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, profilingQueue, new ThreadFactory() {

			@Override
			public Thread newThread(Runnable r) {
				final Thread t = new Thread(r);
				t.setDaemon(true);
				t.setPriority(Thread.NORM_PRIORITY);
				t.setName("Profiler-" + nextThreadNo.getAndIncrement());
				t.setDaemon(true);

				return t;
			}
		});

		final Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				BenchmarkTest.this.measureTimes(profilingQueue);
			}
		}, "Profiler");

		t.setDaemon(true);
		t.start();
	}

	private void measureTime(long worked, ThreadInfo threadInfo) {
		TimeElement child = this.element;
		boolean gotStart = false;
		boolean last = false;

		boolean inDb = false;
		if (threadInfo == null) {
			return;
		}

		for (int i = threadInfo.getStackTrace().length - 1; i >= 0; i--) {
			final StackTraceElement stElement = threadInfo.getStackTrace()[i];
			if (this.isInDb(stElement)) {
				inDb = true;
				break;
			}
		}

		for (int i = threadInfo.getStackTrace().length - 1; i >= 0; i--) {
			final StackTraceElement stElement = threadInfo.getStackTrace()[i];

			if (!gotStart && !stElement.getMethodName().startsWith("singleTest")) {
				continue;
			}

			gotStart = true;

			final String key = BenchmarkTest.SUMMARIZE ? //
				stElement.getClassName() + "." + stElement.getMethodName() : //
				stElement.getClassName() + "." + stElement.getMethodName() + "." + stElement.getLineNumber();

			child = child.get(key);
			TimeElement child2 = this.elements.get(key);
			if (child2 == null) {
				this.elements.put(key, child2 = new TimeElement(key));
			}

			if (this.isInDb(stElement) || (i == 0)) {
				child.addTime(worked, true, inDb);
				child2.addTime(worked, true, inDb);
				last = true;
			}
			else {
				child.addTime(worked, false, inDb);
				child2.addTime(worked, false, inDb);
			}

			if (last) {
				break;
			}
		}
	}

	private void measureTimes(Queue<Runnable> profilingQueue) {
		try {
			this.element = new TimeElement("");

			// get the MXBean
			final ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();

			// wait till the warm up period is over
			while (!BenchmarkTest.this.isRunning()) {
				try {
					Thread.sleep(1);
				}
				catch (final InterruptedException e) {}
			}

			try {
				Thread.sleep(100);
			}
			catch (final InterruptedException e1) {}

			// initialize the start times of the threads
			for (int i = 0; i < this.threadIds.length; i++) {
				this.currentThreadTimes[i] = mxBean.getThreadCpuTime(this.threadIds[i]);
			}

			// profile until the benchmark is over
			while (BenchmarkTest.this.isRunning()) {
				try {
					this.measureTimes(mxBean, profilingQueue);

					this.totalSamples++;

					Thread.sleep(BenchmarkTest.SAMPLE_RATE / 1000, BenchmarkTest.SAMPLE_RATE % 1000);
				}
				catch (final InterruptedException e) {}
			}
		}
		catch (final Exception e) {
			BenchmarkTest.LOG.fatal(e, "");
		}
	}

	private void measureTimes(final ThreadMXBean mxBean, Queue<Runnable> profilingQueue) {
		final ThreadInfo[] threadInfos = mxBean.getThreadInfo(this.threadIds, Integer.MAX_VALUE);

		for (int i = 0; i < this.threadIds.length; i++) {
			final long id = this.threadIds[i];
			final ThreadInfo threadInfo = threadInfos[i];

			final long newThreadTime = mxBean.getThreadCpuTime(id);
			final long worked = Math.abs(newThreadTime - this.currentThreadTimes[i]);

			profilingQueue.add(new Runnable() {

				@Override
				public void run() {
					BenchmarkTest.this.measureTime(worked, threadInfo);
				}
			});
		}
	}

	private EntityManager open(final EntityManagerFactory emf) {
		final EntityManager em = emf.createEntityManager();

		em.getTransaction().begin();

		return em;
	}

	private synchronized void setRunning(boolean running) {
		this.running = running;
	}

	private void singleTest(final EntityManagerFactory emf, Person[][] persons, CriteriaQuery<Address> cq, ParameterExpression<Person> p) {
		this.doBenchmarkPersist(emf, persons);

		this.doBenchmarkFind(emf, persons);

		this.doUpdate(emf, persons);

		this.doBenchmarkCriteria(emf, persons, cq, p);

		this.doBenchmarkJpql(emf, persons);

		this.doRemove(emf, persons);
	}

	private void test(Type type, final EntityManagerFactory emf, Queue<Runnable> workQueue, int length) {
		this.type = type;

		final CriteriaBuilder cb = emf.getCriteriaBuilder();
		final CriteriaQuery<Address> cq = cb.createQuery(Address.class);

		final Root<Person> r = cq.from(Person.class);
		final Join<Person, Address> a = r.join("addresses");
		a.fetch("country", JoinType.LEFT);
		a.fetch("person", JoinType.LEFT);
		cq.select(a);

		final ParameterExpression<Person> p = cb.parameter(Person.class);
		cq.where(cb.equal(r, p));

		for (int i = 0; i < length; i++) {
			workQueue.add(new Runnable() {

				@Override
				public void run() {
					try {
						BenchmarkTest.this.singleTest(emf, BenchmarkTest.this.createPersons(), cq, p);
					}
					catch (final Exception e) {
						BenchmarkTest.LOG.error(e, "Error while running the test");
					}
				}
			});
		}
	}

	/**
	 * Main test entry point
	 * 
	 * @since $version
	 */
	@Test
	public void testJpa() {
		this.type = Type.valueOf(BenchmarkTest.getProp("test", "batoo").toUpperCase(Locale.ENGLISH));

		Thread.currentThread().setContextClassLoader(new TestClassLoader(BenchmarkTest.DB, Thread.currentThread().getContextClassLoader()));

		BenchmarkTest.LOG.info("Benchmark will be run for {0}", this.type);

		BenchmarkTest.LOG.info("Deploying the persistence unit...");

		final EntityManagerFactory emf = Persistence.createEntityManagerFactory(this.type.name().toLowerCase());

		BenchmarkTest.LOG.info("Done deploying the persistence unit.");

		BenchmarkTest.LOG.info("Deploying the persistence unit...");

		final EntityManager em = this.open(emf);
		this.country = new Country();

		this.country.setName("Turkey");
		em.persist(this.country);

		this.close(em);

		this.threadIds = new long[BenchmarkTest.THREAD_COUNT];
		this.currentThreadTimes = new long[BenchmarkTest.THREAD_COUNT];

		BenchmarkTest.LOG.info("Done preparing the countries");

		BenchmarkTest.LOG.info("Running the warm up phase with {0} threads, {1} iterations...", BenchmarkTest.THREAD_COUNT, BenchmarkTest.ITERATIONS / 5);
		LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();
		ExecutorService pool = this.createExecutor(workQueue);

		// warm mup
		this.test(this.type, emf, workQueue, BenchmarkTest.ITERATIONS / 5);
		this.waitUntilFinish(workQueue, pool);

		BenchmarkTest.LOG.info("Done running warm up phase");

		BenchmarkTest.LOG.info("Starting the benchmark with {0} threads, {1} iterations...", BenchmarkTest.THREAD_COUNT, BenchmarkTest.ITERATIONS);

		workQueue = new LinkedBlockingQueue<Runnable>();
		pool = this.createExecutor(workQueue);

		final long started = System.currentTimeMillis();
		// for real
		this.test(this.type, emf, workQueue, BenchmarkTest.ITERATIONS);
		this.setRunning(true);
		this.waitUntilFinish(workQueue, pool);
		this.totalTime = (System.currentTimeMillis() - started);

		BenchmarkTest.LOG.info("Benchmark has been completed...");

		try {
			Thread.sleep(1000);
		}
		catch (final InterruptedException e) {}

		emf.close();
	}

	private void waitUntilFinish(LinkedBlockingQueue<Runnable> workQueue, ExecutorService pool) {
		try {
			final long started = System.currentTimeMillis();

			while (!workQueue.isEmpty()) {
				BenchmarkTest.LOG.info("{0} iterations to go...", workQueue.size());

				if ((System.currentTimeMillis() - started) > BenchmarkTest.MAX_TEST_TIME) {
					throw new IllegalStateException("Max allowed test time exceeded");
				}

				for (int i = 0; i < 250; i++) {
					if (workQueue.isEmpty()) {
						break;
					}

					Thread.sleep(10);
				}
			}

			pool.shutdown();

			if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
				BenchmarkTest.LOG.warn("Forcefully shutting down the thread pool");

				pool.shutdownNow();
			}

			BenchmarkTest.LOG.warn("Iterations completed");
		}
		catch (final InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}

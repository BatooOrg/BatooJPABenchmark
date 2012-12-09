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
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import javax.management.ObjectName;
import javax.management.StandardMBean;
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

import com.google.common.collect.Maps;

/**
 * The actual benchmark implementation that is ran in its own process for complete isolation.
 * 
 * @author hceylan
 * @since $version
 */
public class Benchmark extends AbstractBenchmark implements BenchmarkMBean {

	/**
	 * The main enty point.
	 * 
	 * @param args
	 *            the run arguments
	 * @throws Exception
	 *             in case any error occurs
	 * 
	 * @since $version
	 */
	public static void main(String[] args) throws Exception {
		final Benchmark benchmark = new Benchmark();

		// register the kill hook
		final StandardMBean standardMBean = new StandardMBean(benchmark, BenchmarkMBean.class);
		ManagementFactory.getPlatformMBeanServer().registerMBean(standardMBean, new ObjectName(BenchmarkMBean.OBJECT_NAME));

		// run the actual benchmark
		benchmark.testJpa();

		// sleep till the profiler tells us to quit
		while (true) {
			try {
				Thread.sleep(100);
			}
			catch (final InterruptedException e) {}
		}
	}

	private Country country;
	private boolean running;
	private boolean started;

	private void close(final EntityManager em) {
		em.getTransaction().commit();

		em.close();
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
			final Person[] persons = people[i];
			for (Person person : persons) {
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

	/**
	 * {@inheritDoc}
	 * 
	 */
	@Override
	public boolean hasStarted() {
		return this.started;
	}

	/**
	 * {@inheritDoc}
	 * 
	 */
	@Override
	public boolean isRunning() {
		return this.running;
	}

	/**
	 * {@inheritDoc}
	 * 
	 */
	@Override
	public void kill() {
		System.exit(0);
	}

	private EntityManager open(final EntityManagerFactory emf) {
		final EntityManager em = emf.createEntityManager();

		em.getTransaction().begin();

		return em;
	}

	private void singleTest(final EntityManagerFactory emf, Person[][] persons, CriteriaQuery<Address> cq, ParameterExpression<Person> p) {
		this.doBenchmarkPersist(emf, persons);

		this.doBenchmarkFind(emf, persons);

		this.doUpdate(emf, persons);

		this.doBenchmarkCriteria(emf, persons, cq, p);

		this.doBenchmarkJpql(emf, persons);

		this.doRemove(emf, persons);
	}

	private void test(final EntityManagerFactory emf, Queue<Runnable> workQueue, int length) {
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
						Benchmark.this.singleTest(emf, Benchmark.this.createPersons(), cq, p);
					}
					catch (final Exception e) {
						AbstractBenchmark.LOG.error(e, "Error while running the test");
					}
				}
			});
		}
	}

	/**
	 * Main test entry point.
	 * 
	 * @since $version
	 */
	private void testJpa() {
		final TestClassLoader classLoader = new TestClassLoader(AbstractBenchmark.DB, Thread.currentThread().getContextClassLoader());
		Thread.currentThread().setContextClassLoader(classLoader);

		AbstractBenchmark.LOG.info("Benchmark will be run for {0}@{1}", AbstractBenchmark.TYPE.name().toLowerCase(Locale.ENGLISH), AbstractBenchmark.DB);

		AbstractBenchmark.LOG.info("Deploying the persistence unit...");

		try {
			final Map<String, Object> properties = Maps.newHashMap();
			properties.put("eclipselink.persistencexml", classLoader.getPersistenceXmlPath());

			final EntityManagerFactory emf = Persistence.createEntityManagerFactory(AbstractBenchmark.TYPE.name().toLowerCase(), properties);

			AbstractBenchmark.LOG.info("Done deploying the persistence unit.");

			AbstractBenchmark.LOG.info("Deploying the persistence unit...");

			final EntityManager em = this.open(emf);
			this.country = new Country();

			this.country.setName("Turkey");
			em.persist(this.country);

			this.close(em);

			AbstractBenchmark.LOG.info("Done preparing the countries");

			AbstractBenchmark.LOG.info("Running the warm up phase with {0} threads, {1} iterations...", AbstractBenchmark.THREAD_COUNT,
				AbstractBenchmark.ITERATIONS / 10);
			LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();
			ThreadPoolExecutor pool = this.createExecutor(workQueue, "Warmup");

			// warm mup
			this.test(emf, workQueue, AbstractBenchmark.ITERATIONS / 10);
			this.waitUntilFinish(pool);

			AbstractBenchmark.LOG.info("Done running warm up phase");

			AbstractBenchmark.LOG.info("Starting the benchmark with {0} threads, {1} iterations...", AbstractBenchmark.THREAD_COUNT,
				AbstractBenchmark.ITERATIONS);

			workQueue = new LinkedBlockingQueue<Runnable>();
			pool = this.createExecutor(workQueue, "BM");

			this.started = true;
			this.running = true;
			this.test(emf, workQueue, AbstractBenchmark.ITERATIONS);
			this.waitUntilFinish(pool);

			AbstractBenchmark.LOG.info("Benchmark has been completed...");

			this.running = false;

			// give it some time to settle down
			try {
				Thread.sleep(1000);
			}
			catch (final InterruptedException e) {}

			emf.close();
		}
		catch (final Throwable t) {
			AbstractBenchmark.LOG.error(t, "An error occurred while running the benchmark");

			throw new RuntimeException(t);
		}
	}
}

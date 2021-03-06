/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.hornetq.tests.integration.client;

import junit.framework.Assert;

import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.tests.util.ServiceTestBase;

/**
 *
 * A TransactionDurabilityTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 * Created 16 Jan 2009 11:00:33
 *
 *
 */
public class TransactionDurabilityTest extends ServiceTestBase
{

   /*
    * This tests the following situation:
    *
    * (With the old implementation)
    * Currently when a new persistent message is routed to persistent queues, the message is first stored, then the message is routed.
    * Let's say it has been routed to two different queues A, B.
    * Ref R1 gets consumed and acknowledged by transacted session S1, this decrements the ref count and causes an acknowledge record to be written to storage,
    * transactionally, but it's not committed yet.
    * Ref R2 then gets consumed and acknowledged by non transacted session S2, this causes a delete record to be written to storage.
    * R1 then rolls back, and the server is restarted - unfortunatelt since the delete record was written R1 is not ready to be consumed again.
    *
    * It's therefore crucial the messages aren't deleted from storage until AFTER any ack records are committed to storage.
    *
    *
    */
   public void testRolledBackAcknowledgeWithSameMessageAckedByOtherSession() throws Exception
   {
      Configuration conf = createDefaultConfig();

      final SimpleString testAddress = new SimpleString("testAddress");

      final SimpleString queue1 = new SimpleString("queue1");

      final SimpleString queue2 = new SimpleString("queue2");

      HornetQServer server = createServer(true, conf);

      server.start();

      ServerLocator locator =
               addServerLocator(HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(
                                                                                                      ServiceTestBase.INVM_CONNECTOR_FACTORY)));

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session1 = sf.createSession(false, true, true);

      ClientSession session2 = sf.createSession(false, false, false);

      session1.createQueue(testAddress, queue1, null, true);

      session1.createQueue(testAddress, queue2, null, true);

      ClientProducer producer = session1.createProducer(testAddress);

      ClientMessage message = session1.createMessage(true);

      producer.send(message);

      session1.start();

      session2.start();

      ClientConsumer consumer1 = session1.createConsumer(queue1);

      ClientConsumer consumer2 = session2.createConsumer(queue2);

      ClientMessage m1 = consumer1.receive(1000);

      Assert.assertNotNull(m1);

      ClientMessage m2 = consumer2.receive(1000);

      Assert.assertNotNull(m2);

      m2.acknowledge();

      // Don't commit session 2

      m1.acknowledge();

      session2.rollback();

      session1.close();

      session2.close();

      server.stop();

      server.start();

      sf = createSessionFactory(locator);

      session1 = sf.createSession(false, true, true);

      session2 = sf.createSession(false, true, true);

      session1.start();

      session2.start();

      consumer1 = session1.createConsumer(queue1);

      consumer2 = session2.createConsumer(queue2);

      m1 = consumer1.receiveImmediate();

      Assert.assertNull(m1);

      m2 = consumer2.receive(1000);

      Assert.assertNotNull(m2);

      m2.acknowledge();

      session1.close();

      session2.close();

      server.stop();

      server.start();

      sf = createSessionFactory(locator);

      session1 = sf.createSession(false, true, true);

      session2 = sf.createSession(false, true, true);

      session1.start();

      session2.start();

      consumer1 = session1.createConsumer(queue1);

      consumer2 = session2.createConsumer(queue2);

      m1 = consumer1.receiveImmediate();

      Assert.assertNull(m1);

      m2 = consumer2.receiveImmediate();

      Assert.assertNull(m2);

      session1.close();

      session2.close();

      locator.close();

      server.stop();

   }

}

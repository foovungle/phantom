/*
 * Copyright 2013-2015 Websudos, Limited.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Explicit consent must be obtained from the copyright owner, Websudos Limited before any redistribution is made.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.websudos.phantom.zookeeper

import java.net.InetSocketAddress
import scala.concurrent.duration._

import com.websudos.phantom.connectors.{CassandraProperties, DefaultCassandraManager}
import com.websudos.util.testing._
import com.websudos.util.zookeeper.ZooKeeperInstance
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}


class ZookeeperConnectorTest extends FlatSpec with Matchers with BeforeAndAfterAll with CassandraSetup {

  implicit def patience: PatienceConfiguration.Timeout = timeout(5 seconds)

  val instance = new ZooKeeperInstance("/cassandra")

  override def beforeAll(): Unit = {
    super.beforeAll()
    setupCassandra()
    instance.start()
    DefaultZookeeperManagers.defaultManager.initIfNotInited(TestTable.keySpace.name)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    instance.stop()
  }

  it should "correctly use the default localhost:2181 connector address if no environment variable has been set" in {
    System.setProperty(CassandraProperties.ZookeeperEnvironmentString, "")

    TestTable.manager.defaultZkAddress.getHostName shouldEqual "0.0.0.0"

    TestTable.manager.defaultZkAddress.getPort shouldEqual 2181

  }

  it should "use the values from the environment variable if they are set" in {
    System.setProperty(CassandraProperties.ZookeeperEnvironmentString, "localhost:4902")

    TestTable.manager.defaultZkAddress.getHostName shouldEqual "localhost"

    TestTable.manager.defaultZkAddress.getPort shouldEqual 4902
  }

  it should "return the default if the environment property is in invalid format" in {

    System.setProperty(CassandraProperties.ZookeeperEnvironmentString, "localhost:invalidint")

    TestTable.manager.defaultZkAddress.getHostName shouldEqual "0.0.0.0"

    TestTable.manager.defaultZkAddress.getPort shouldEqual 2181
  }

  it should "correctly retrieve the Cassandra series of ports from the Zookeeper cluster" in {
    instance.client.getData(TestTable.zkPath, watch = false) successful {
      res => {
        info("Ports correctly retrieved from Cassandra.")
        new String(res.data) shouldEqual s"localhost:${DefaultCassandraManager.cassandraPort()}"
      }
    }
  }

  it should "match the Zookeeper connector string to the spawned instance settings" in {
    System.setProperty(CassandraProperties.ZookeeperEnvironmentString, instance.zookeeperConnectString)
    TestTable.manager.defaultZkAddress shouldEqual instance.address
  }

  it should "correctly retrieve the Sequence of InetSocketAddresses from zookeeper" in {

    TestTable.manager.store.hostnamePortPairs.successful {
      res => {
        res shouldEqual Seq(new InetSocketAddress("localhost", DefaultCassandraManager.cassandraPort()))
      }
    }
  }
}

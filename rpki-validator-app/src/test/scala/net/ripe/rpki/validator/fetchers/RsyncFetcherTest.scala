/**
 * The BSD License
 *
 * Copyright (c) 2010-2012 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
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
package net.ripe.rpki.validator.fetchers

import java.net.URI

import net.ripe.rpki.validator.config.ApplicationOptions
import net.ripe.rpki.validator.models.validation.{BrokenObject, RepositoryObject}
import net.ripe.rpki.validator.support.ValidatorTestCase
import org.junit.Ignore
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
@Ignore
class RsyncFetcherTest extends ValidatorTestCase with BeforeAndAfter with MockitoSugar {

  test("Should download repository") {
    val fetcher = new RsyncFetcher(FetcherConfig(rsyncDir = ApplicationOptions.rsyncDirLocation))

    System.gc()
    Thread.sleep(2000)
    val heapSize = Runtime.getRuntime.totalMemory
    var objects = List[RepositoryObject[_]]()

    fetcher.fetchRepo(new URI("rsync://rpki.ripe.net/repository/"), new FetcherListener {
      override def processObject(repoObj: RepositoryObject[_]) = {
        objects = repoObj :: objects
      }
      override def processBroken(brokenObj: BrokenObject): Unit = {}
      override def withdraw(url: URI, hash: String): Unit = {}
    })
    System.gc()
    Thread.sleep(2000)
    val heapSize2 = Runtime.getRuntime.totalMemory
    println(objects.size)
    println(s"heapSize = $heapSize, heapSize2 = $heapSize2, diff = ${heapSize2 - heapSize}")
  }

}
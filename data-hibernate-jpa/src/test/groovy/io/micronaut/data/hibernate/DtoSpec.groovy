/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.hibernate

import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.AuthorBooksDto
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.BookDto
import io.micronaut.data.tck.entities.Shipment
import io.micronaut.data.tck.entities.ShipmentId
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.hibernate.Hibernate
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(rollback = false, transactional = false, packages = "io.micronaut.data.tck.entities")
@H2DBProperties
class DtoSpec extends Specification {

    @Inject
    @Shared
    BookRepository bookRepository

    @Inject
    @Shared
    BookDtoRepository bookDtoRepository

    @Inject
    @Shared
    JpaShipmentRepository shipmentRepository

    def setup() {
        bookRepository.saveAuthorBooks([
                new AuthorBooksDto("Stephen King", Arrays.asList(
                        new BookDto("The Stand", 1000),
                        new BookDto("Pet Cemetery", 400)
                )),
                new AuthorBooksDto("James Patterson", Arrays.asList(
                        new BookDto("Along Came a Spider", 300),
                        new BookDto("Double Cross", 300)
                )),
                new AuthorBooksDto("Don Winslow", Arrays.asList(
                        new BookDto("The Power of the Dog", 600),
                        new BookDto("The Border", 700)
                ))])
    }

    def cleanup() {
        bookRepository.deleteAll()
    }

    void "test entity graph"() {
        when:
        def results = bookRepository.findAllByTitleStartsWith("The")

        then:
        results.size() == 3
        results.every({ Book b -> Hibernate.isInitialized(b.author)})
    }

    void "test no entity graph"() {
        when:
        def results = bookRepository.findAllByTitleStartingWith("The")

        then:
        results.every({ Book b -> !Hibernate.isInitialized(b.author)})
        results.size() == 3
    }

    void "test dto projection"() {
        when:
        def results = bookDtoRepository.findByTitleLike("The%")

        then:
        results.size() == 3
        results.every { it instanceof BookDto }
        results.every { it.title.startsWith("The")}
        bookDtoRepository.findOneByTitle("The Stand").title == "The Stand"

        when:"paged result check"
        def result = bookDtoRepository.searchByTitleLike("The%", Pageable.from(0))

        then:"the result is correct"
        result.totalSize == 3
        result.size == 10
        result.content.every { it instanceof BookDto }
        result.content.every { it.title.startsWith("The")}
    }

    void "test dto projection with embedded id"() {
        given:
        def id = new ShipmentId("a", "b")
        shipmentRepository.save(new Shipment(id, "test"))

        def id2 = new ShipmentId("a", "c")
        shipmentRepository.save(new Shipment(id2, "test2"))

        def id3 = new ShipmentId("b", "d")
        shipmentRepository.save(new Shipment(id3, "test3"))
        when:
        def shipments = shipmentRepository.findAllByShipmentIdCountry("a")
        def shipmentDtos = shipmentRepository.queryAllByShipmentIdCountry("a")
        then:
        shipmentDtos.size() == 2
        shipmentDtos.every{ it.shipmentId()}
        shipmentDtos.every{ it.shipmentId().country == "a"}
        shipments.size() == 2
        cleanup:
        shipmentRepository.deleteAll()
    }

}

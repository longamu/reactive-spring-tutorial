package max.lab.rst.s05;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import max.lab.rst.domain.Book;
import max.lab.rst.domain.BookQuery;
import max.lab.rst.domain.InMemoryDataSource;
import max.lab.rst.s03.C04ReactiveControllerHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@RequiredArgsConstructor
@Configuration
public class C02BookControllerWithR2dbc {
    private static final String PATH_PREFIX = "/routed-r2dbc/";

    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final C01BookRepository bookRepository;

    @Bean("r2dbcBookRouters")
    public RouterFunction<ServerResponse> routers() {
        return RouterFunctions.route()
                .POST(PATH_PREFIX + "book", this::create)
                .GET(PATH_PREFIX + "books", this::findAll)
                .GET(PATH_PREFIX + "query-books", this::findByPage)
                .GET(PATH_PREFIX + "book/{isbn}", this::find)
                .PUT(PATH_PREFIX + "book/{isbn}", this::update)
                .DELETE(PATH_PREFIX + "book/{isbn}", this::delete)
                .build();
    }

    private Mono<ServerResponse> findByPage(ServerRequest request) {
        return C04ReactiveControllerHelper.queryParamsToMono(request, objectMapper,
                BookQuery.class, validator)
                .flatMap(query -> ServerResponse.ok()
                        .body(bookRepository.findBooksByQuery(query), Book.class));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        var isbn = request.pathVariable("isbn");
        return bookRepository.findById(isbn)
                .flatMap(book -> bookRepository.delete(isbn)
                        .then(ServerResponse.ok().build())).switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> update(ServerRequest request) {
        var isbn = request.pathVariable("isbn");
        return bookRepository.findById(isbn)
                .flatMap(book ->
                                C04ReactiveControllerHelper
                                        .requestBodyToMono(request, validator, Book.class)
                                        .flatMap(aBook ->
                                                bookRepository.update(aBook)
                                                        .then(ServerResponse.ok().build()))
                ).switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> find(ServerRequest request) {
        var isbn = request.pathVariable("isbn");
        return bookRepository.findById(isbn)
                .flatMap(book -> ServerResponse.ok().bodyValue(book))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> findAll(ServerRequest request) {
        return ServerResponse.ok().body(bookRepository.findAll(), Book.class);
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return C04ReactiveControllerHelper.requestBodyToMono(request, validator,
                (t, errors) -> InMemoryDataSource.findBookMonoById(t.getIsbn())
                        .map((book -> {
                            errors.rejectValue("isbn", "already.exists", "Already exists");
                            return Tuples.of(book, errors);
                        }))
                , Book.class)
                .flatMap(book -> bookRepository.insert(book).thenReturn(book))
                .flatMap(book -> ServerResponse.created(
                        UriComponentsBuilder.fromHttpRequest(request.exchange().getRequest())
                                .path("/").path(book.getIsbn()).build().toUri())
                        .build());
    }
}

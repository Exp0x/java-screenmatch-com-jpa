package br.com.alura.screenmatch.principal;

import br.com.alura.screenmatch.model.Categoria;
import br.com.alura.screenmatch.model.DadosSerie;
import br.com.alura.screenmatch.model.DadosTemporada;
import br.com.alura.screenmatch.model.Episodio;
import br.com.alura.screenmatch.model.Serie;
import br.com.alura.screenmatch.repository.SerieRepository;
import br.com.alura.screenmatch.service.ConsumoApi;
import br.com.alura.screenmatch.service.ConverteDados;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Principal {

    private Scanner leitura = new Scanner(System.in);
    private ConsumoApi consumo = new ConsumoApi();
    private ConverteDados conversor = new ConverteDados();
    private final String ENDERECO = "https://www.omdbapi.com/?t=";
    // private List<DadosSerie> DadosSeries = new ArrayList<>();
    private List<Serie> series = new ArrayList<>();

    @Autowired
    private SerieRepository repositorioSerie;

    private final String apiKey = Optional.ofNullable(System.getenv("API_KEY_OMDB"))
            .orElseThrow(() -> new IllegalStateException("API_KEY_OMDB env var is not defined"));

    public void exibeMenu() {
        int opcao = -1;
        while (opcao != 0) {
            var menu = """
                    1 - Buscar séries
                    2 - Buscar episódios
                    3 - Listar séries buscadas
                    4 - Buscar séries por Titulo
                    5 - Buscar séries por Ator
                    6 - Top 5 séries
                    7 - Buscar séries por categoria/gênero
                    8 - Filtrar séries

                    0 - Sair
                    """;

            System.out.println(menu);
            opcao = leitura.nextInt();
            leitura.nextLine();

            switch (opcao) {
                case 1:
                    buscarSerieWeb();
                    break;
                case 2:
                    buscarEpisodioPorSerie();
                    break;
                case 3:
                    listarSeriesBuscadas();
                    break;
                case 4:
                    buscarSeriePorTitulo();
                    break;
                case 5:
                    buscarSeriePorAtor();
                    break;
                case 6:
                    buscarTop5Series();
                    break;
                case 7:
                    buscarSeriePorGenero();
                    break;
                case 8:
                    filtrarSeriesPorTemporadaEAvaliacao();
                    break;
                case 0:
                    System.out.println("Saindo...");
                    break;
                default:
                    System.out.println("Opção inválida");
            }
        }
    }


    private void buscarSerieWeb() {
        DadosSerie dados = getDadosSerie();
        Serie serie = new Serie(dados);
        repositorioSerie.save(serie);
        System.out.println(serie);
    }

    private DadosSerie getDadosSerie() {
        System.out.println("Digite o nome da série para busca");
        var nomeSerie = leitura.nextLine();
        try {
            nomeSerie = URLEncoder.encode(nomeSerie, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        var json = consumo.obterDados(ENDERECO + nomeSerie + apiKey);
        DadosSerie dados = conversor.obterDados(json, DadosSerie.class);
        return dados;
    }

    private void buscarEpisodioPorSerie() {
        listarSeriesBuscadas();
        System.out.println("Escolha uma serie pelo nome: ");
        var nomeSerie = leitura.nextLine();

        Optional<Serie> serie = repositorioSerie.findByTituloContainingIgnoreCase(nomeSerie);
        if (serie.isEmpty()) {
            System.out.println("Serie não encontrada");
            return;
        }

        var serieEncontrada = serie.get();

        List<DadosTemporada> temporadas = new ArrayList<>();

        for (int i = 1; i <= serieEncontrada.getTotalTemporadas(); i++) {
            var json = consumo
                    .obterDados(ENDERECO + serieEncontrada.getTitulo().replace(" ", "+") + "&season=" + i + apiKey);
            DadosTemporada dadosTemporada = conversor.obterDados(json, DadosTemporada.class);
            temporadas.add(dadosTemporada);

        }

        List<Episodio> episodios = temporadas.stream()
                .flatMap(d -> d.episodios().stream()
                        .map(e -> new Episodio(d.numero(), e)))
                .collect(Collectors.toList());
        serieEncontrada.setEpisodios(episodios);
        repositorioSerie.save(serieEncontrada);
    }

    private void listarSeriesBuscadas() {
        series = repositorioSerie.findAll();
        series.stream()
                .sorted(Comparator.comparing(Serie::getGenero))
                .forEach(System.out::println);
    }

    private void buscarSeriePorTitulo() {
        System.out.println("Escolha uma serie pelo nome: ");
        var nomeSerie = leitura.nextLine();
        Optional<Serie> serieBuscada = repositorioSerie.findByTituloContainingIgnoreCase(nomeSerie);
        if (serieBuscada.isEmpty()) {
            System.out.println("Serie não encontrada");
            return;
        }
        System.out.println("Dados da Serie: " + serieBuscada.get());
    }

    private void buscarSeriePorAtor() {
        System.out.println("Digite o nome do Ator");
        var nomeAtor = leitura.nextLine();
        System.out.println("Digite a avaliação minima");
        var avaliacao = leitura.nextDouble();
        List<Serie> serieBuscada = repositorioSerie
                .findByAtoresContainingIgnoreCaseAndAvaliacaoGreaterThanEqual(nomeAtor, avaliacao);
        System.out.println("Séries que em que " + nomeAtor + " trabalhou: ");
        serieBuscada.forEach(s -> System.out.println(s.getTitulo() + "  Avaliação: " + s.getAvaliacao()));
    }

    private void buscarTop5Series() {
        List<Serie> seriesTop = repositorioSerie.findTop5ByOrderByAvaliacaoDesc();
        seriesTop.forEach(s -> System.out.println(s.getTitulo() + "  Avaliação: " + s.getAvaliacao()));
    }

    private void buscarSeriePorGenero() {
        System.out.println("Digite o gênero/categoria da série");
        var generoSerie = leitura.nextLine();
        Categoria categoria = Categoria.fromPortugues(generoSerie);
        List<Serie> series = repositorioSerie.findByGenero(categoria);
        System.out.println("Séries da categoria: " + generoSerie);
        series.forEach(s -> System.out.println(s.getTitulo() + "  Avaliação: " + s.getAvaliacao()));
    }

    private void filtrarSeriesPorTemporadaEAvaliacao() {
        System.out.println("Filtrar séries até quantas temporadas? ");
        var totalTemporada = leitura.nextInt();
        leitura.nextLine();
        System.out.println("Com avaliação a partir de que valor? ");
        var avaliacao = leitura.nextDouble();
        leitura.nextLine();
        List<Serie> filtroSeries = repositorioSerie.findByTotalTemporadasLessThanEqualAndAvaliacaoGreaterThanEqual(totalTemporada, avaliacao);
        System.out.println("---Séries filtradas---");
        filtroSeries.forEach(s -> System.out.println(s.getTitulo() + " - avaliação: " + s.getAvaliacao()));
    }
}
package com.mg.betterreadsdataloader;

import com.mg.betterreadsdataloader.author.Author;
import com.mg.betterreadsdataloader.author.AuthorRepository;
import com.mg.betterreadsdataloader.book.Book;
import com.mg.betterreadsdataloader.book.BookRepository;
import com.mg.betterreadsdataloader.connections.DataStaxAstraProperties;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
@EnableConfigurationProperties(DataStaxAstraProperties.class)
public class BetterreadsDataLoaderApplication {

	@Autowired
	AuthorRepository authorRepository;

	@Autowired
	BookRepository bookRepository;

	@Value("${datadump.location.author}")
	private String authorDumpLocation;

	@Value("${datadump.location.works}")
	private String worksDumpLocation;

	public static void main(String[] args) {
		SpringApplication.run(BetterreadsDataLoaderApplication.class, args);
	}

	private void initAuthors(){
		Path path = Paths.get(authorDumpLocation);
		try(Stream<String> lines = Files.lines(path)){
			//read and parse the line
			//construct author object
			//persists using author repository
			lines.forEach(line ->{
				String jsonString = line.substring(line.indexOf("{"));
				try {
					JSONObject jsonObject = new JSONObject(jsonString);
					Author author = new Author();
					author.setName(jsonObject.optString("name"));
					author.setPersonalName(jsonObject.optString("personal_name"));
					author.setId(jsonObject.optString("key").replace("/authors/",""));
					authorRepository.save(author);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			});

		}
		catch (IOException e){
			e.printStackTrace();
		}
	}

	private void initWorks(){
		Path path = Paths.get(worksDumpLocation);
		try(Stream<String> lines = Files.lines(path)){
			//read and parse the line
			//construct book object
			//persists using book repository
			DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
			lines.forEach(line ->{
				String jsonString = line.substring(line.indexOf("{"));
				try {
					JSONObject jsonObject = new JSONObject(jsonString);
					Book book = new Book();

					book.setName(jsonObject.optString("title"));

					JSONObject descriptionJsonObj = jsonObject.optJSONObject("description");
					if(descriptionJsonObj!=null) {
						book.setDescription(descriptionJsonObj.optString("value"));
					}

					JSONObject publishedJsonobj = jsonObject.optJSONObject("created");
					if(publishedJsonobj!=null){
						book.setPublishedDate(LocalDate.parse(publishedJsonobj.optString("value"),dateFormat));
					}

					JSONArray coversJsonArr = jsonObject.optJSONArray("covers");
					if(coversJsonArr!=null){
						List<String> coverIds = new ArrayList<>();
						for(int i=0 ;i<coversJsonArr.length();i++){
							coverIds.add(coversJsonArr.getString(i));
						}
						book.setCoverIds(coverIds);
					}

					JSONArray authorsJsonArr = jsonObject.optJSONArray("authors");
					if(authorsJsonArr!=null){
						List<String> authorIds = new ArrayList<>();
						for(int i=0;i<authorsJsonArr.length();i++){
							String authorId = authorsJsonArr.getJSONObject(i).getJSONObject("author").getString("key").replace("/authors/", "");
							authorIds.add(authorId);
						}
						book.setAuthorIds(authorIds);
						List<String> authorNames = authorIds.stream().map(id -> authorRepository.findById(id))
								.map(optionalAuthor -> {
									if (!optionalAuthor.isPresent()) return "Unknown Author";
									return optionalAuthor.get().getName();
								}).collect(Collectors.toList());
						book.setAuthorNames(authorNames);
					}


					book.setId(jsonObject.getString("key").replace("/works/",""));
                    System.out.println("Saving book" + book.getName());
					bookRepository.save(book);

				} catch (JSONException e) {
					e.printStackTrace();
				}

			});
		}
		catch (IOException e){
			e.printStackTrace();
		}



	}

	@PostConstruct
	public void start(){
		System.out.println(authorDumpLocation);
		//initAuthors();
		System.out.println(worksDumpLocation);
		//initWorks();
//		Author author = new Author();
//		author.setId("1");
//		author.setName("Muskaan");
//		author.setPersonalName("Mg");
//		authorRepository.save(author);
	}


	@Bean
	public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties){
		Path bundle = astraProperties.getSecureConnectBundle().toPath();
		return builder -> builder.withCloudSecureConnectBundle(bundle);
	}
}

package service.ontology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import service.io.FileService;

@Stateless
public class OntologyService {

	@Inject
	private OntModel ontology;
	
	@Inject
	private FileService fileUtil;

	public List<Individual> listIndividualsByClassURI(String classURI) {
		OntClass atividadeClass = ontology.getOntClass(classURI);
		return ontology.listIndividuals(atividadeClass).toList();
	}

	public List<String> getPropertieValue(Resource resource, String propertieURI) {
		Property propertieOnt = ontology.getProperty(propertieURI);
		NodeIterator iterator = ontology.listObjectsOfProperty(resource, propertieOnt);
		return iterator.toList().stream().map(node -> node.toString().toLowerCase()).collect(Collectors.toList());
	}
	
	public List<String> getPropertieValueOfResourceOfPropertie(Resource resource, String propertieURI, String propertieValueURI) {
		Property propertieOnt = ontology.getProperty(propertieURI);
		Property propertieValueOnt = ontology.getProperty(propertieValueURI);

		NodeIterator iterator = ontology.listObjectsOfProperty(resource, propertieOnt);

		List<RDFNode> values = iterator.toList().stream()
				.map(node -> ontology.listObjectsOfProperty(node.asResource(), propertieValueOnt))
				.flatMap(nodeIter -> nodeIter.toList().stream())
				.collect(Collectors.toList());
		
		return values.stream().map(RDFNode::toString).collect(Collectors.toList());
	}

	public List<RDFNode> listRDFNodesBySparql(String sparqlFile, Map<String, String> params, String paramNameRDFNode) throws IOException {
		List<QuerySolution> solutions = executeSparql(sparqlFile, params);
		return solutions.stream().map(solution -> solution.get(paramNameRDFNode)).collect(Collectors.toList());
	}
	
	private List<QuerySolution> executeSparql(String sparqlFile, Map<String, String> params) throws IOException {
		String teste = fileUtil.readStringFromURI(String.format("sparql/oficial/%s.rf", sparqlFile));
		Query query = QueryFactory.create(teste);
		QuerySolutionMap queryMap = new QuerySolutionMap();
		params.entrySet().forEach(pair -> {
			queryMap.add(pair.getKey(), ontology.createTypedLiteral(pair.getValue()));
		});

		QueryExecution qe = QueryExecutionFactory.create(query, ontology, queryMap);
		ResultSet results = qe.execSelect();
		List<QuerySolution> solutions = new ArrayList<>();
		while (results.hasNext()) {
			solutions.add(results.next());
		}
		qe.close();
		return solutions;
	}
	
	public List<String> filterTermosFindedInOntology(String propertieURI, List<String> termos) throws IOException {
		Property propertieOnt = ontology.getProperty(propertieURI);
		return termos.stream()
				.filter(termo -> ontology.listSubjectsWithProperty(propertieOnt, termo).hasNext())
				.collect(Collectors.toList());
	}

	public List<RDFNode> listRDFNodesByPropertieValue(String propertieURI, List<String> values) throws IOException {
		Property propertieOnt = ontology.getProperty(propertieURI);
		List<RDFNode> nodes = values.stream()
				.map(label -> ontology.listSubjectsWithProperty(propertieOnt, label))
				.flatMap(ri -> ri.toList().stream())
				.distinct()
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		return nodes;
	}

	public List<Individual> listindividualsByClass(String classSubjectURI) {
		OntClass classOnt = ontology.getOntClass(classSubjectURI);
		return ontology.listIndividuals(classOnt).toList();
	}

	public List<Resource> filterResourceWithAllPropertieParams(String propertieURI, List<Resource> individualsToFilter, List<RDFNode> params) throws IOException {
		Property propertieOnt = ontology.getProperty(propertieURI);
		return individualsToFilter.stream()
				.filter(individual -> ontology.listObjectsOfProperty(individual, propertieOnt).toList().containsAll(params))
				.collect(Collectors.toList());
	}

	public List<Resource> filterResourceNotHasExcecaoToParams(List<Resource> individualsToFilter, List<RDFNode> paramsResource, List<String> params) throws IOException {
		Property excetoElementoprop = ontology.getProperty(MEIResource.PROP_EXCETOELEMENTO);
		Property excetoCondicaoProp = ontology.getProperty(MEIResource.PROP_EXCETOCONDICAO);
		Property temElementoProp = ontology.getProperty(MEIResource.PROP_TEMELEMENTO);
		Property descricaoElementoProp = ontology.getProperty(MEIResource.PROP_ATIVIDADEELEMENTODESCRICAO);

		List<Resource> resourcesWithExcecao = paramsResource.stream()
				.map(param -> ontology.listResourcesWithProperty(excetoElementoprop, param))
				.flatMap(resIter -> resIter.toList().stream())
				.distinct()
				.collect(Collectors.toList());

		individualsToFilter = individualsToFilter.stream().filter(indivi -> {
			NodeIterator ni = ontology.listObjectsOfProperty(indivi, excetoCondicaoProp);
			return !ni.toList().stream().anyMatch(condicao -> {
				NodeIterator termosNodeIter = ontology.listObjectsOfProperty(condicao.asResource(), temElementoProp);
				return termosNodeIter.toList().stream().allMatch(termo -> {
					NodeIterator descricaoNodeIter = ontology.listObjectsOfProperty(termo.asResource(), descricaoElementoProp);
					return descricaoNodeIter.toList().stream().anyMatch(descr -> params.contains(descr.toString()));
				});
			});
		}).collect(Collectors.toList());

		individualsToFilter.removeAll(resourcesWithExcecao);
		return individualsToFilter;
	}

}

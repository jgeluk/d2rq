package de.fuberlin.wiwiss.d2rq.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import com.hp.hpl.jena.vocabulary.RDF;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.helpers.CSVParser;
import de.fuberlin.wiwiss.d2rq.helpers.Logger;
import de.fuberlin.wiwiss.d2rq.map.Column;
import de.fuberlin.wiwiss.d2rq.map.D2RQ;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.Join;
import de.fuberlin.wiwiss.d2rq.map.NodeMaker;
import de.fuberlin.wiwiss.d2rq.map.PropertyBridge;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable;
import de.fuberlin.wiwiss.d2rq.map.URIMatchPolicy;

/**
 * Creates D2RQ domain classes (like {@link PropertyBridge},
 * {@link TranslationTable} from a Jena model representation
 * of a D2RQ mapping file. Checks the map for consistency.
 * 
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @version $Id: MapParser.java,v 1.7 2006/09/02 11:38:15 cyganiak Exp $
 */
public class MapParser {
	private Model model;
	private Graph graph;
	private String baseURI;
	private Collection propertyBridges = new ArrayList();
	private Map nodesToDatabases = new HashMap();
	private Map nodesToClassMapSpecs = new HashMap();
	private Map nodesToTranslationTables = new HashMap();
	private Map processingInstructions = new HashMap();
	
	/**
	 * Constructs a new MapParser from a Jena model containing the RDF statements
	 * from a D2RQ mapping file.
	 * @param mapModel a Jena model containing the RDF statements from a D2RQ mapping file
	 */
	public MapParser(Model mapModel, String baseURI) {
		this.model = mapModel;
		this.graph = mapModel.getGraph();
		this.baseURI = baseURI;
	}
	
	/**
	 * Starts the parsing process. Must be called before results can be retrieved
	 * from the getter methods.
	 */
	public void parse() {
	    parseProcessingInstructions();
		parseDatabases();
		parseClassMaps();
		parsePropertyBridges();
		parseAdditionalProperties();
		parseClassMapTypes();
		checkColumnTypes();
	}

	public Collection getDatabases() {
		return this.nodesToDatabases.values();
	}
	
	public Collection getPropertyBridges() {
		return this.propertyBridges;
	}
	
	public Map getProcessingInstructions() {
	    return this.processingInstructions;
	}
	
	private void parseProcessingInstructions() {
		Iterator it = this.graph.find(Node.ANY, RDF.Nodes.type, D2RQ.ProcessingInstructions);
		while (it.hasNext()) {
			Node instructions = ((Triple) it.next()).getSubject();
			// predicate is key, object is value => false parameter (XML style)
			processingInstructions.putAll(findLiteralsAsMap(instructions, Node.ANY, null, false,false));
		}
	}
	
	private void parseDatabases() {
	    ExtendedIterator it = this.graph.find(Node.ANY, RDF.Nodes.type, D2RQ.Database);
	    if (!it.hasNext()) {
	        Logger.instance().error("No d2rq:Database defined in the mapping file.");
	    }
		while (it.hasNext()) {
			Node dbNode = ((Triple) it.next()).getSubject();
			this.nodesToDatabases.put(dbNode, buildDatabase(dbNode));
		}
	}
	
	private Database databaseForNode(Node node) {
		return (Database) this.nodesToDatabases.get(node);
	}

	private static Map d2rqColumnTypeToDatabaseColumnType;
	
	private Database buildDatabase(Node node) {
		String odbcDSN = findZeroOrOneLiteral(node, D2RQ.odbcDSN);
		String jdbcDSN = findZeroOrOneLiteral(node, D2RQ.jdbcDSN);
		String jdbcDriver = findZeroOrOneLiteral(node, D2RQ.jdbcDriver);
		String username = findZeroOrOneLiteral(node, D2RQ.username);
		String password = findZeroOrOneLiteral(node, D2RQ.password);
		String allowDistinct = findZeroOrOneLiteral(node, D2RQ.allowDistinct);
		String expressionTranslator = findZeroOrOneLiteral(node, D2RQ.expressionTranslator);
		
		if (d2rqColumnTypeToDatabaseColumnType==null) {
		    d2rqColumnTypeToDatabaseColumnType=new HashMap();
		    d2rqColumnTypeToDatabaseColumnType.put(D2RQ.textColumn,Database.textColumn);
		    d2rqColumnTypeToDatabaseColumnType.put(D2RQ.numericColumn,Database.numericColumn);
		    d2rqColumnTypeToDatabaseColumnType.put(D2RQ.dateColumn,Database.dateColumn);		    
		}
		Map columnTypes = new HashMap();
		columnTypes.putAll(findLiteralsAsMap(node, D2RQ.textColumn, d2rqColumnTypeToDatabaseColumnType));
		columnTypes.putAll(findLiteralsAsMap(node, D2RQ.numericColumn, d2rqColumnTypeToDatabaseColumnType));
		columnTypes.putAll(findLiteralsAsMap(node, D2RQ.dateColumn, d2rqColumnTypeToDatabaseColumnType));
		if (jdbcDSN != null && jdbcDriver == null || jdbcDSN == null && jdbcDriver != null) {
			Logger.instance().error("d2rq:jdbcDSN and d2rq:jdbcDriver must be used together");
		}
		Database db = new Database(odbcDSN, jdbcDSN, jdbcDriver, username, password, columnTypes);
		if (allowDistinct!=null) {
		    if (allowDistinct.equals("true"))
		        db.setAllowDistinct(true);
		    else if (allowDistinct.equals("false"))
		        db.setAllowDistinct(false);
		    else 
		        Logger.instance().error("d2rq:allowDistinct value must be true or false");			
		}
		if (expressionTranslator!=null)
		    db.setExpressionTranslator(expressionTranslator);
		return db;
	}

	private void parseClassMaps() {
		ExtendedIterator it = this.graph.find(Node.ANY, RDF.Nodes.type, D2RQ.ClassMap);
		while (it.hasNext()) {
			Node classMapNode = ((Triple) it.next()).getSubject();
			Node dbNode = findOneNode(classMapNode, D2RQ.dataStorage);
			Database db = databaseForNode(dbNode);
			if (db == null) {
				Logger.instance().error("Unknown d2rq:dataStorage for d2rq:ClassMap " +
						classMapNode);
			}
			NodeMakerSpec spec = buildResourceSpec(classMapNode, db, true);
			this.nodesToClassMapSpecs.put(classMapNode, spec);
		}
	}

	private NodeMakerSpec classMapSpecForNode(Node node) {
		return (NodeMakerSpec) this.nodesToClassMapSpecs.get(node);
	}

	private void assertHasColumnTypes(NodeMaker nodeMaker, Database database) {
		Iterator it = nodeMaker.getColumns().iterator();
		while (it.hasNext()) {
			Column column = (Column) it.next();
			database.assertHasType(nodeMaker.getAliases().originalOf(column));			
		}
	}

	public TranslationTable getTranslationTable(Node node) {
		if (this.nodesToTranslationTables.containsKey(node)) {
			return (TranslationTable) this.nodesToTranslationTables.get(node);
		}
		TranslationTable translationTable = new TranslationTable();
		String href = findZeroOrOneLiteralOrURI(node, D2RQ.href);
		if (href != null) {
			translationTable.addAll(new CSVParser(href).parse());
		}
		String className = findZeroOrOneLiteral(node, D2RQ.javaClass);
		if (className != null) {
			translationTable.setTranslatorClass(className, toResource(node));
		}
		ExtendedIterator it = this.graph.find(node, D2RQ.translation, Node.ANY);
		if (href == null && className == null && !it.hasNext()) {
			Logger.instance().warning("TranslationTable " + node + " contains no translations");
		}
		if (className != null && (href != null || it.hasNext())) {
			Logger.instance().warning("Can't combine d2rq:javaClass with d2rq:translation or d2rq:href on " + node);
		}
		while (it.hasNext()) {
			Node translation = ((Triple) it.next()).getObject();
			String dbValue = findOneLiteral(translation, D2RQ.databaseValue);
			String rdfValue = findOneLiteralOrURI(translation, D2RQ.rdfValue);
			translationTable.addTranslation(dbValue, rdfValue);
		}
		this.nodesToTranslationTables.put(node, translationTable);
		return translationTable;
	}

	private void parsePropertyBridges() {
		Iterator it = this.graph.find(Node.ANY, D2RQ.belongsToClassMap, Node.ANY);
		while (it.hasNext()) {
			Triple t = (Triple) it.next();
			Node propBridgeNode = t.getSubject();
			Node classMapNode = t.getObject();
			parsePropertyBridge(propBridgeNode, classMapNode);
		}
	}
	
	private void parsePropertyBridge(Node bridgeNode, Node classMapNode) {
		NodeMakerSpec subjectSpec = classMapSpecForNode(classMapNode);
		if (subjectSpec == null) {
			Logger.instance().error("d2rq:belongsToClassMap for " +
					bridgeNode + " is no d2rq:ClassMap");
			return;
		}
		if (this.graph.find(bridgeNode, D2RQ.belongsToClassMap, Node.ANY).toList().size() > 1) {
			Logger.instance().error("Multiple d2rq:belongsToClassMap in " + bridgeNode);
			return;
		}
		Set propertiesForBridge = findPropertiesForBridge(bridgeNode);
		if (propertiesForBridge.isEmpty()) {
			Logger.instance().error("Missing d2rq:property for PropertyBridge " + bridgeNode);
			return;
		}
		Iterator it = propertiesForBridge.iterator();
		while (it.hasNext()) {
			Node property = (Node) it.next();
			PropertyBridge bridge = createPropertyBridge(
					classMapNode,
					bridgeNode,
					subjectSpec,
					NodeMakerSpec.createFixed(property),
					buildObjectSpec(bridgeNode, subjectSpec.database()));
			registerBridgeForClassMap(classMapNode, bridge);
		}
	}

	private NodeMakerSpec buildObjectSpec(Node node, Database database) {
		NodeMakerSpec spec = buildResourceSpec(node, database, false);
		Node refersTo = findZeroOrOneNode(node, D2RQ.refersToClassMap);
		if (refersTo != null) {
			NodeMakerSpec otherSpec = classMapSpecForNode(refersTo);
			if (otherSpec == null) {
				throw new D2RQException("d2rq:refersToClassMap of " + node + " is no valid d2rq:ClassMap");
			}
			if (database != null && !database.equals(otherSpec.database())) {
				throw new D2RQException("d2rq:dataStorages for " + node + " don't match");
			}
			spec.wrapExisting(otherSpec);
		}
		String columnName = findZeroOrOneLiteral(node, D2RQ.column);
		if (columnName != null) {
			if (isObjectPropertyBridge(node)) {
				spec.setURIColumn(columnName);
			} else {
				spec.setLiteralColumn(columnName);
			}
		}
		String pattern = findZeroOrOneLiteral(node, D2RQ.pattern);
		if (pattern != null) {
			if (isObjectPropertyBridge(node)) {
				spec.setURIPattern(ensureIsAbsolute(pattern));
			} else {
				spec.setLiteralPattern(pattern);
			}
		}
		String datatype = findZeroOrOneLiteralOrURI(node, D2RQ.datatype);
		if (datatype != null) {
			spec.setDatatypeURI(datatype);
		}
		String lang = findZeroOrOneLiteral(node, D2RQ.lang);
		if (lang != null) {
			spec.setLang(lang);
		}
		return spec;
	}
	
	private NodeMakerSpec buildResourceSpec(Node node, Database database, boolean defaultToUnique) {
		NodeMakerSpec spec = new NodeMakerSpec(node.toString());
		spec.setDatabase(database);
		String bNodeIdColumns = findZeroOrOneLiteral(node, D2RQ.bNodeIdColumns);
		if (bNodeIdColumns != null) {
			spec.setBlankColumns(bNodeIdColumns);
		}
		String uriColumnName = findZeroOrOneLiteral(node, D2RQ.uriColumn);
		if (uriColumnName != null) {
			spec.setURIColumn(uriColumnName);
		}
		String uriPattern = findZeroOrOneLiteral(node, D2RQ.uriPattern);
		if (uriPattern != null) {
			spec.setURIPattern(ensureIsAbsolute(uriPattern));
		}
		String valueRegex = findZeroOrOneLiteral(node, D2RQ.valueRegex);
		if (valueRegex != null) {
			spec.setRegexHint(valueRegex);
		}
		String valueContains = findZeroOrOneLiteral(node, D2RQ.valueContains);
		if (valueContains != null) {
			spec.setContainsHint(valueContains);
		}
		String valueMaxLength = findZeroOrOneLiteral(node, D2RQ.valueMaxLength);
		if (valueMaxLength != null) {
			try {
				int maxLength = Integer.parseInt(valueMaxLength);
				spec.setMaxLengthHint(maxLength);
			} catch (NumberFormatException nfex) {
				Logger.instance().warning("Ignoring d2rq:valueMaxLength \"" +
						valueMaxLength + "\" on " + node + " (must be an integer)");
			}
		}
		Node translateWith = findZeroOrOneNode(node, D2RQ.translateWith);
		if (translateWith != null) {
			TranslationTable table = getTranslationTable(translateWith);
			if (table == null) {
				Logger.instance().error("Unknown d2rq:translateWith in " + node);
			}
			spec.setTranslationTable(table);
		}
		spec.addJoins(Join.buildJoins(findLiterals(node, D2RQ.join)));
		spec.addConditions(findLiterals(node, D2RQ.condition));
		spec.setAliases(findLiterals(node, D2RQ.alias));
		boolean isUnique = defaultToUnique;
		String containsDuplicates = findZeroOrOneLiteral(node, D2RQ.containsDuplicates);
		if ("true".equals(containsDuplicates)) {
			isUnique = false;
		} else if ("false".equals(containsDuplicates)) {
			isUnique = true;
		} else if (containsDuplicates != null) {
			Logger.instance().error("Illegal value '" + containsDuplicates + "' for d2rq:containsDuplicates on " + node);
		}
		if (isUnique) {
			spec.setIsUnique();
		}
		return spec;
	}
	
	private boolean isObjectPropertyBridge(Node node) {
		return this.graph.contains(node, RDF.Nodes.type, D2RQ.ObjectPropertyBridge);
	}

	private PropertyBridge createPropertyBridge(Node classMap, Node node, 
			NodeMakerSpec subjectsSpec, NodeMakerSpec predicatesSpec, NodeMakerSpec objectsSpec) {
		URIMatchPolicy policy = new URIMatchPolicy();
		policy.setSubjectBasedOnURIColumn(subjectsSpec.isURIColumnSpec());
		policy.setSubjectBasedOnURIPattern(subjectsSpec.isURIPatternSpec());
		policy.setObjectBasedOnURIColumn(objectsSpec.isURIColumnSpec());
		policy.setObjectBasedOnURIPattern(objectsSpec.isURIPatternSpec());
		NodeMaker subjects = subjectsSpec.build();
		NodeMaker predicates = predicatesSpec.build();
		NodeMaker objects = objectsSpec.build();
		PropertyBridge bridge = new PropertyBridge(node,
				subjects, predicates, objects,
				subjectsSpec.database(), policy);
		this.propertyBridges.add(bridge);
		registerBridgeForClassMap(classMap, bridge);
		return bridge;
	}

	private Set findPropertiesForBridge(Node bridge) {
		Set results = new HashSet();
		Iterator it = this.graph.find(Node.ANY, D2RQ.propertyBridge, bridge);
		while (it.hasNext()) {
			Triple t = (Triple) it.next();
			results.add(t.getSubject());
		}
		it = this.graph.find(bridge, D2RQ.property, Node.ANY);
		while (it.hasNext()) {
			Triple t = (Triple) it.next();
			results.add(t.getObject());
		}
		return results;
	}

	private void parseAdditionalProperties() {
		ExtendedIterator it = this.graph.find(Node.ANY, D2RQ.additionalProperty, Node.ANY);
		while (it.hasNext()) {
			Triple t = (Triple) it.next();
			NodeMakerSpec subjectSpec = classMapSpecForNode(t.getSubject());
			if (subjectSpec == null) {
				Logger.instance().warning("Ignoring d2rq:additionalProperty on " +
						t.getSubject() + " as they are allowed only on d2rq:ClassMaps");
				continue;
			}
			createPropertyBridge(
					t.getSubject(),
					t.getObject(),
					subjectSpec,
					NodeMakerSpec.createFixed(findOneNode(t.getObject(), D2RQ.propertyName)),
					NodeMakerSpec.createFixed(findOneNode(t.getObject(), D2RQ.propertyValue)));
		}
	}

	private void parseClassMapTypes() {
		ExtendedIterator it = this.graph.find(Node.ANY, D2RQ.classMap, Node.ANY);
		while (it.hasNext()) {
			Triple t = (Triple) it.next();
			addRDFTypePropertyBridge(t.getObject(), t.getSubject());
		}
		it = this.graph.find(Node.ANY, D2RQ.class_, Node.ANY);
		while (it.hasNext()) {
			Triple t = (Triple) it.next();
			addRDFTypePropertyBridge(t.getSubject(), t.getObject());
		}
	}

	private void addRDFTypePropertyBridge(Node toClassMap, Node rdfsClass) {
		NodeMakerSpec spec = classMapSpecForNode(toClassMap);
		if (spec == null) {
			Logger.instance().error(toClassMap + ", referenced from " +
					rdfsClass + ", is no d2rq:ClassMap");
			return;
		}
		createPropertyBridge(toClassMap, Node.createAnon(), 
				spec,
				NodeMakerSpec.createFixed(RDF.Nodes.type),
				NodeMakerSpec.createFixed(rdfsClass));
	}

	private void checkColumnTypes() {
		Iterator it = this.propertyBridges.iterator();
		while (it.hasNext()) {
			PropertyBridge bridge = (PropertyBridge) it.next();
			assertHasColumnTypes(bridge.getSubjectMaker(), bridge.getDatabase());
			assertHasColumnTypes(bridge.getPredicateMaker(), bridge.getDatabase());
			assertHasColumnTypes(bridge.getObjectMaker(), bridge.getDatabase());
		}
	}
	
	private Resource toResource(Node node) {
		return this.model.getResource(node.getURI());
	}

	private String findZeroOrOneLiteral(Node subject, Node predicate) {
		Node node = findZeroOrOneNode(subject, predicate);
		if (node == null) {
			return null;
		}
		if (!node.isLiteral()) {
			Logger.instance().error(toQName(predicate) + " for " + subject + " must be literal");
			return null;
		}
		return node.getLiteral().getLexicalForm();
	}

	private String findZeroOrOneLiteralOrURI(Node subject, Node predicate) {
		Node node = findZeroOrOneNode(subject, predicate);
		if (node == null) {
			return null;
		}
		if (node.isLiteral()) {
			return node.getLiteral().getLexicalForm();
		} else if (node.isURI()) {
			return node.getURI();
		}
		Logger.instance().error(toQName(predicate) + " for " + subject + " must be literal or URI");
		return null;
	}

	private Node findZeroOrOneNode(Node subject, Node predicate) {
		ExtendedIterator it = this.graph.find(subject, predicate, Node.ANY);
		if (!it.hasNext()) {
			return null;
		}
		Node result = ((Triple) it.next()).getObject();
		if (it.hasNext()) {
			Logger.instance().warning("Ignoring multiple " + toQName(predicate) + " on " + subject);
			return null;
		}
		return result;
	}

	private Node findOneNode(Node subject, Node predicate) {
		ExtendedIterator it = this.graph.find(subject, predicate, Node.ANY);
		if (!it.hasNext()) {
			Logger.instance().error("Missing " + toQName(predicate) +
					" in " + toQName(subject));
			return null;
		}
		Node result = ((Triple) it.next()).getObject();
		if (it.hasNext()) {
			Logger.instance().warning("Ignoring multiple " + toQName(predicate) + " on " + subject);
		}
		return result;
	}

	private String findOneLiteral(Node subject, Node predicate) {
		Node node = findOneNode(subject, predicate);
		if (!node.isLiteral()) {
			Logger.instance().error(toQName(predicate) + " for " + subject + " must be literal");
			return null;
		}
		return node.getLiteral().getLexicalForm();
	}

	private String findOneLiteralOrURI(Node subject, Node predicate) {
		Node node = findOneNode(subject, predicate);
		if (node.isLiteral()) {
			return node.getLiteral().getLexicalForm();
		} else if (node.isURI()) {
			return node.getURI();
		}
		Logger.instance().error(toQName(predicate) + " for " + subject + " must be literal or URI");
		return null;
	}

	private Set findLiterals(Node subject, Node predicate) {
		Set result = new HashSet(3);
		ExtendedIterator it = this.graph.find(subject, predicate, Node.ANY);
		while (it.hasNext()) {
			Node node = ((Triple) it.next()).getObject();
			if (!node.isLiteral()) {
				Logger.instance().error(toQName(predicate) + " for " + subject + " must be literal");
				return null;
			}
			result.add(node.getLiteral().getLexicalForm());
		}
		return result;
	}

	private Map findLiteralsAsMap(Node subject, Node predicate, Map predicateToObjectMap) {
	    return findLiteralsAsMap(subject, predicate, predicateToObjectMap, true, true);
	}
	private Map findLiteralsAsMap(Node subject, Node predicate, Map predicateToObjectMap, boolean objectIsKey, boolean warnIfNotLiteral) {
		Map result = new HashMap();
		ExtendedIterator itColText = this.graph.find(subject, predicate, Node.ANY);
		while (itColText.hasNext()) {
			Triple t = (Triple) itColText.next();
			subject=t.getSubject();
			predicate=t.getPredicate();
			Node object=t.getObject();
			if (!object.isLiteral()) {
			    if (warnIfNotLiteral) {
			        Logger.instance().warning("Ignoring non-literal " + toQName(predicate) +
						" for " + subject + " (\"" + object + "\")");
			    }
				continue;
			}
			String objectString=object.getLiteral().getLexicalForm();
			Object predicateValue=(predicateToObjectMap==null)? predicate : predicateToObjectMap.get(predicate);
//			if (value==null) {
//			    throw new RuntimeException("Unmapped database type " + predicate);
//			}
			if (objectIsKey) // most cases
			    result.put(objectString, predicateValue); // put(key, value)
			else // xml style
			    result.put(predicateValue, objectString); 
		}
		return result;
	}

	private String toQName(Node node) {
		return node.toString(this.model);
	}
	
	private String ensureIsAbsolute(String uriPattern) {
		if (uriPattern.indexOf(":") == -1) {
			return this.baseURI + uriPattern;
		}
		return uriPattern;
	}

	/**
	 * TODO This section was added as a quick hack for D2R Server 0.3 and should probably not be here
	 */
	private Map nodesToClassMapBridgeLists = new HashMap();
	private Map nodesToClassMapMakers = new HashMap();
	
	public Map propertyBridgesByClassMap() {
		return this.nodesToClassMapBridgeLists;
	}

	public Map NodeMakersByClassMap() {
		return this.nodesToClassMapMakers;
	}
	
	private void registerBridgeForClassMap(Node classMap, PropertyBridge bridge) {
		List bridgeList = (List) this.nodesToClassMapBridgeLists.get(classMap);
		if (bridgeList == null) {
			bridgeList = new ArrayList();
			this.nodesToClassMapBridgeLists.put(classMap, bridgeList);
		}
		bridgeList.add(bridge);
		nodesToClassMapMakers.put(classMap, bridge.getSubjectMaker());
	}
}
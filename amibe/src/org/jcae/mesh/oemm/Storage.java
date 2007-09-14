/* jCAE stand for Java Computer Aided Engineering. Features are : Small CAD
   modeler, Finite element mesher, Plugin architecture.

    Copyright (C) 2007, by EADS France

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA */

package org.jcae.mesh.oemm;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import gnu.trove.TIntArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.jcae.mesh.amibe.ds.Mesh;
import org.jcae.mesh.amibe.ds.Triangle;
import org.jcae.mesh.amibe.ds.Vertex;
import org.jcae.mesh.amibe.ds.AbstractTriangle;
import org.jcae.mesh.amibe.ds.AbstractVertex;
import org.jcae.mesh.oemm.OEMM.Node;
import org.apache.log4j.Logger;

/**
 * This class converts between disk and memory formats.
 * {@link org.jcae.mesh.MeshOEMMIndex} is an example to show how to
 * convert a triangle soup into an out-of-core mesh data structure.
 * When an OEMM is generated on disk, octants can be loaded and unloaded
 * on demand.  An {@link OEMM} instance is first created by calling
 * {@link #readOEMMStructure}.  Then {@link MeshReader#buildMesh} can be called
 * to select which octants are loaded into memory.
 *
 * As can be seen in {@link #readOEMMStructure}, {@link OEMM} instances
 * are serialized on disk.  But this is different with partial mesh data
 * structure.  Each child octant has a local number between 0 and 7 depending
 * on its spatial localization.  A similar structure is kept on disk, each
 * child octant is put into a seperate directory named after its number.
 * Octant leaves contain vertex and triangle data.  A file with a "v" suffix
 * contains coordinates of vertices stored as 3 double values.  A file with
 * a "a" suffix contains for each vertex the list of leaves which are
 * connected to this vertex.  This allows setting <code>readable</code> and
 * <code>writable</code> attributes, as explained in original paper.
 * A file with a "t" suffix contains for each triangle 6 int values, the first
 * 3 are leaf numbers for its 3 vertices, and last 3 are local vertex number
 * in their respective leaf.
 */
public class Storage
{
	private static Logger logger = Logger.getLogger(Storage.class);	
	
	/**
	 * Number of bytes per triangle.  On disk a triangle is represented by
	 * <ul>
	 *  <li>3 int : leaf numbers for each vertex</li>
	 *  <li>3 int : local vertex indices</li>
	 *  <li>1 int : group number</li>
	 * </ul>
	 */
	protected static final int TRIANGLE_SIZE = 28;

	/**
	 * Number of bytes per vertex.  On disk a vertex is represented by 3 double
	 * (coordinates).
	 */
	protected static final int VERTEX_SIZE = 24;

	/**
	 * Buffer size.  Vertices and triangles are read through buffers to improve
	 * efficiency, buffer size must be a multiple of {@link #TRIANGLE_SIZE} and
	 * {@link #VERTEX_SIZE}.
	 */
	protected static final int bufferSize = 24 * VERTEX_SIZE * TRIANGLE_SIZE;
	// bufferSize = 16128
	
	/**
	 * Buffer to improve I/O efficiency.
	 */
	protected static ByteBuffer bb = ByteBuffer.allocate(bufferSize);
	
	/**
	 * Creates an {@link OEMM} instance from its disk representation.
	 *
	 * @param dir   directory containing disk representation
	 * @return an {@link OEMM} instance
	 */
	public static OEMM readOEMMStructure(String dir)
	{
		OEMM ret = new OEMM(dir);
		logger.info("Build an OEMM from "+ret.getFileName());
		try
		{
			ObjectInputStream os = new ObjectInputStream(new FileInputStream(new File(ret.getFileName())));
			ret = (OEMM) os.readObject();
			// Reset nr_leaves and nr_cells because they are
			// incremented by OEMM.insert()
			int nrl = ((Integer) os.readObject()).intValue();
			ret.clearNodes();
			ret.leaves = new OEMM.Node[nrl];
			for (int i = 0; i < nrl; i++)
			{
				OEMM.Node n = (OEMM.Node) os.readObject();
				ret.insert(n);
				ret.leaves[i] = n;
			}
			os.close();
			ret.setDirectory(dir);
			ret.printInfos();
		}
		catch (IOException ex)
		{
			logger.error("I/O error when reading indexed file in "+dir);
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
		catch (ClassNotFoundException ex)
		{
			logger.error("I/O error when reading indexed file in "+dir);
			ex.printStackTrace();
			throw new RuntimeException();
		}
		return ret;
	}
	
	/**
	 * Stores an {@link OEMM} instance to its disk representation.
	 *
	 * @param oemm stored object
	 */
	public static void storeOEMMStructure(OEMM oemm)
	{
		if (logger.isInfoEnabled()) {
			logger.info("storeOEMMStructure");
		}
		ObjectOutputStream oos = null;
		try {
			oos = new ObjectOutputStream(new FileOutputStream(new File(oemm.getFileName())));
			oos.writeObject(oemm);
			oos.writeObject(new Integer(oemm.leaves.length));
			for (OEMM.Node node : oemm.leaves) {
				oos.writeObject(node);
			}
			
		} catch (IOException e) {
			logger.error("I/O error when reading indexed file in " + oemm.getDirectory());
			e.printStackTrace();
			throw new RuntimeException(e);
		} finally {
			if (oos != null) {
				try {
					oos.close();
				} catch(IOException e) {
					//ignore this
				}
			}
		}
	}
	
	/**
	 * Saves mesh on the disk into octree structure. The o-nodes, that 
	 * will be saved, are deduced from mesh (from position of vertices). 
	 *
	 * @param oemm OEMM instance
	 * @param mesh  mesh to be stored onto disk
	 * @param storedLeaves  set of leaves to store
	 * TODO There is no support for moving vertices into another octree node. 
	 */
	public static void saveNodes(OEMM oemm, Mesh mesh, Set<Integer> storedLeaves)
	{
		if (logger.isInfoEnabled()) {
			logger.info("saveNodes started");
		}
		removeNonReferencedVertices(mesh);
		// For each Vertex, find its enclosing octant leaf.
		// Side-effect: storedLeaves may be modified if new leaves have to be added.
		Map<Vertex, Integer> mapVertexToLeafindex = getMapVertexToLeafindex(oemm, mesh, storedLeaves);
		storeVertices(oemm, mesh, storedLeaves, mapVertexToLeafindex);
		storeTriangles(oemm, mesh, storedLeaves, mapVertexToLeafindex);
		
		storeOEMMStructure(oemm);
		if (logger.isInfoEnabled()) {
			logger.info("saveNodes ended");
		}
	}
	
	/**
	 * Removes vertices from mesh that are not in any triangle of mesh except vertices 
	 * that are read only. 
	 * @param mesh
	 */
	private static void removeNonReferencedVertices(Mesh mesh)
	{
		List<AbstractVertex> tempCollection = new ArrayList<AbstractVertex>();
		tempCollection.addAll(mesh.getNodes());
		mesh.getNodes().clear();
		
		//actually we do not need create map (set is enough) but we index with label of vertex 
		// - it should be faster. Also, we could control that there is 
		// no different vertices with the same label.
		Map<Integer, Vertex> referencedVertices = getMapLabelToVertex(mesh);
		
		Set<Integer> processedVertIndex = new HashSet<Integer>();
		for (Vertex v: referencedVertices.values()) 
		{
			mesh.add(v);
			processedVertIndex.add(Integer.valueOf(v.getLabel()));
		}
		for (AbstractVertex av: tempCollection)
		{
			Vertex vert = (Vertex) av;
			Integer label = Integer.valueOf(vert.getLabel());
			if (processedVertIndex.contains(label))
				continue;
			processedVertIndex.add(label);
			if (!vert.isWritable()) {
				mesh.add(vert);
			}
		}
	}
	
	/**
	 * Returns a map of vertex label into vertex.
	 *
	 * @param mesh  mesh
	 * @return a map of vertex label into vertex.
	 * @throws RuntimeException There are different vertices with the same label in the mesh.
	 */
	private static Map<Integer, Vertex> getMapLabelToVertex(Mesh mesh)
	{
		Map<Integer, Vertex> referencedVertices = new HashMap<Integer, Vertex>(mesh.getTriangles().size() / 2);
		for(AbstractTriangle tr: mesh.getTriangles())
		{
			for (int i = 0; i < 3; i++)
			{
				Vertex vertex = tr.vertex[i];
				if (!vertex.isReadable() || vertex instanceof FakeNonReadVertex) {
					continue;
				}
				//check that there are no different vertices with the same label
				Integer label = Integer.valueOf(vertex.getLabel());
				Vertex oldVertex = referencedVertices.get(label);
				if (oldVertex != null && oldVertex != vertex)
					throw new RuntimeException("There are different vertices with the same label!");
				referencedVertices.put(label, vertex);
			}
		}
		return referencedVertices;
	}
	
	/**
	 * Locates mesh vertices.
	 *
	 * @param oemm
	 * @param mesh
	 * @param storedLeaves 
	 * @param  
	 */
	private static Map<Vertex, Integer> getMapVertexToLeafindex(OEMM oemm, Mesh mesh, Set<Integer> storedLeaves)
	{
		int positions[] = new int[3];
		Map<Vertex, Integer> ret = new HashMap<Vertex, Integer>(mesh.getNodes().size());
		for(AbstractVertex av: mesh.getNodes())
		{
			Vertex vertex = (Vertex) av;
			oemm.double2int(vertex.getUV(), positions);
			Node n = null;
			Integer nIdx = null;
			try {
				n = oemm.search(positions);
				nIdx = Integer.valueOf(n.leafIndex);
			} catch (IllegalArgumentException e) {
				//ingore this - try move vertex more closer
				logger.warn("A vertex has moved and requires a new leaf to be created");
				n = createNewNode(oemm, positions);
				nIdx = Integer.valueOf(n.leafIndex);
				
				storedLeaves.add(nIdx);
			}
			ret.put(vertex, nIdx);
		}
		return ret;
	}
	
	/**
	 * Stores vertices of the mesh into oemm structure on the disk. 
	 * @param oemm
	 * @param mesh
	 * @param storedLeaves  set of leaves to store
	 */
	private static void storeVertices(OEMM oemm, Mesh mesh, Set<Integer> storedLeaves, Map<Vertex, Integer> mapVertexToLeafindex)
	{
		Map<Integer, List<Vertex>> mapLeafindexToVertexList = new HashMap<Integer, List<Vertex>>();
		byte[] byteBuffer = new byte[256];
		Map<Integer, VertexIndexHolder> old2newIndex = new HashMap<Integer, VertexIndexHolder>();
		Map<Integer, Integer> new2oldIndex = new HashMap<Integer,Integer>();
		Set<Integer> nodes4Update = new HashSet<Integer>();
		Set<Integer> addedNeighbour = new HashSet<Integer>();
		Set<Integer> movedVertices = new HashSet<Integer>();
		int[] positions = new int[3];
		for (Integer i: storedLeaves)
			mapLeafindexToVertexList.put(i, new ArrayList<Vertex>());
		
		collectAllVertices(oemm, mesh, mapVertexToLeafindex, mapLeafindexToVertexList, movedVertices, storedLeaves);
		for(Entry<Integer, List<Vertex>> entry: mapLeafindexToVertexList.entrySet())
		{
			Node node = oemm.leaves[entry.getKey().intValue()];
			List<Vertex> vertexList = entry.getValue();
			sortVertexList(vertexList);
			
			reindexVerticesInNode(node, vertexList, old2newIndex, new2oldIndex);
			
			List<List<Integer>> adjacencyFileWithoutLoadedNodes = readAdjacencyFile(oemm, node, storedLeaves);
			removeLoadedAdjacentNodes(node, storedLeaves);
			Map<Integer, Byte> nodeIndex2adjIndex = makeNodeIndex2adjIndexMap(node);
			if (vertexList.size() > node.vn) {
				throw new RuntimeException("Cannot add/delete vertex yet");
			}
			
			// Write vertex coordinates
			DataOutputStream fc;
			try {
				fc = new DataOutputStream( new FileOutputStream(getVerticesFile(oemm, node)));
			} catch (FileNotFoundException e) {
				logger.error("I/O error when writing file "+getVerticesFile(oemm, node));
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			try {
				for (Vertex vertex: vertexList)
					writeDoubleArray(fc, vertex.getUV());
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			} finally {
				try {
					fc.close();
				} catch (IOException ex) {
					//ignore this
				}
			}
			
			// Write adjacency
			DataOutputStream afc = null;
			try {
				afc = new DataOutputStream( new FileOutputStream(getAdjacencyFile(oemm, node)));
			} catch (FileNotFoundException e) {
				logger.error("I/O error when writing file "+getAdjacencyFile(oemm, node));
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			try {
				int counter = 0;
				for (Vertex vertex: vertexList) {
					assert ((counter + node.minIndex) == vertex.getLabel());
					
					int neighbours = 0;
					addedNeighbour.clear();
					for (Vertex neighbour: vertex.getNeighboursNodes())
					{
						int nodeNumber;
						Integer InodeNumber = mapVertexToLeafindex.get(neighbour);
						if (InodeNumber != null)
							nodeNumber = InodeNumber.intValue();
						else
						{
							nodeNumber = searchNode(oemm, neighbour, positions);
							InodeNumber = Integer.valueOf(nodeNumber);
						}
						if (nodeNumber != node.leafIndex && !addedNeighbour.contains(InodeNumber))
						{
							if (!nodeIndex2adjIndex.containsKey(InodeNumber))
							{
								//throw new UnsupportedOperationException ("Add adjacent nodes not implemented yet");
								Byte index = Byte.valueOf((byte) (node.adjLeaves.size() & 0xff));
								node.adjLeaves.add(nodeNumber);
								nodeIndex2adjIndex.put(InodeNumber, index);
							}
							byteBuffer[neighbours] = nodeIndex2adjIndex.get(InodeNumber).byteValue();
							neighbours++;
							addedNeighbour.add(InodeNumber);
						}
					}
					//add adjacent non-loaded nodes
					Integer Ilabel = Integer.valueOf(vertex.getLabel());
					Integer lastIndex = getLaterIndex(new2oldIndex, Ilabel);
					int localIndex = lastIndex.intValue() - node.minIndex;
					if (!movedVertices.contains(lastIndex) && localIndex < adjacencyFileWithoutLoadedNodes.size() ) {
						for (Integer adjacent: adjacencyFileWithoutLoadedNodes.get(localIndex))
						{
							if (!addedNeighbour.contains(adjacent)) {
								addedNeighbour.add(adjacent);
								byteBuffer[neighbours] = nodeIndex2adjIndex.get(adjacent).byteValue();
								neighbours++;
							}
						}
						if (new2oldIndex.containsKey(Ilabel)) {
							addRequiredNodes4Update(node, storedLeaves, nodes4Update, byteBuffer, neighbours);
						}
					}
					afc.writeByte(neighbours);
					afc.write(byteBuffer, 0, neighbours);
					counter++;
				}
				node.vn = vertexList.size();
			} catch (IOException e) {
				
				e.printStackTrace();
				throw new RuntimeException(e);
			} finally {
				try {
					afc.close();
				} catch (IOException ex) {
					//ignore this
				}
			}
		}
		
		updateNonReadNodes(oemm, nodes4Update, old2newIndex);
	}

	/**
	 * It browses all nodes of the mesh and it fills map of node index to list 
	 * of contained vertices.
	 * @param oemm
	 * @param mesh
	 * @param mapLeafindexToVertexList
	 * @param storedLeaves 
	 * @param  
	 */
	private static void collectAllVertices(OEMM oemm, Mesh mesh, Map<Vertex, Integer> mapVertexToLeafindex, Map<Integer, List<Vertex>> mapLeafindexToVertexList, Set<Integer> movedVertices, Set<Integer> storedLeaves)
	{
		for(AbstractVertex av: mesh.getNodes())
		{
			Vertex vertex = (Vertex) av;
			Integer nIdx = mapVertexToLeafindex.get(vertex);
			assert nIdx != null : vertex;
			List<Vertex> vertices = mapLeafindexToVertexList.get(nIdx);
			if (vertices == null) {
				throw new UnsupportedOperationException("Cannot put vertex into octree node: "+nIdx+". Node is not loaded!");
			}
			Node n = oemm.leaves[nIdx.intValue()];
			int label = vertex.getLabel();
			if (label >= n.minIndex && label <= n.maxIndex && (label - n.minIndex + 1) > n.vn ) {
				System.out.println("Vertex: " + label + " added!!!");
			}
			if (label < n.minIndex || label > n.maxIndex) {
				//throw new RuntimeException("Cannot move vertex between leafs");
				//experimental implementation
				if (n.getMaxIndex() - n.minIndex < n.vn) {
					throw new UnsupportedOperationException("Cannot put vertex into octree node: " 
							+n.leafIndex + ". It contains " + n.vn + " of vertices.");
				}
				if (!vertex.isWritable()) {
					throw new UnsupportedOperationException("Cannot move non-writable vertices!!");
				}
				int newLabel = n.minIndex + n.vn;
				n.vn++;
				vertex.setLabel(newLabel);
				movedVertices.add(Integer.valueOf(newLabel));
			}
			vertices.add(vertex);
		}
	}
	
	/**
	 * It makes ascending sort of list of vertices in respect of their label.
	 * @param list
	 */
	private static void sortVertexList(List<Vertex> list)
	{
		Collections.sort(list, new Comparator<Vertex>() {
			@Override
			public int compare(Vertex o1, Vertex o2) {
				return (o1.getLabel()<o2.getLabel() ? -1 : (o1.getLabel()==o2.getLabel() ? 0 : 1));
			}
		});
	}
	
	/**
	 * Reindexes vertices in given oemm node. List of vertices must satisfy
	 * for every n in <0,n-1> this condition: label(v_n) + 1 = label(v_n + 1).  
	 * @param node node being reindexed
	 * @param vertices list of vertices contained in the node
	 * @param old2newIndex map of old label to new label
	 * @param new2oldIndex map of new label to old label
	 */
	private static void reindexVerticesInNode(Node node, List<Vertex> vertices,
			Map<Integer, VertexIndexHolder> old2newIndex,
			Map<Integer, Integer> new2oldIndex)
	{
		//assert node.vn == vertices.size();
		Vertex[] values = vertices.toArray(new Vertex[vertices.size()]);
		vertices.clear();
		int upperBound = values.length - 1;
		for (int i = 0; i <= upperBound; i++) {
			//there is hole in labeling. We move vertex from the tail and reindex it
			while (values[i].getLabel() > node.minIndex + vertices.size())
			{
				Vertex moved = values[upperBound]; 
				upperBound--; 
				Integer oldIndex = Integer.valueOf(moved.getLabel());
				//new index is next empty index
				int newIndex = vertices.size() + node.minIndex;
				old2newIndex.put(oldIndex, new VertexIndexHolder(node, newIndex));
				new2oldIndex.put(Integer.valueOf(newIndex), oldIndex);
				moved.setLabel(newIndex);
				vertices.add(moved);
			}
			if (i > upperBound) {
				break;
			}
			vertices.add(values[i]);
		}
	}
	
	/**
	 * Remove adjacent nodes for given node. It removes nodes that will be stored, because
	 * adjacency will be constructed again.
	 * 
	 * @param node
	 * @param storedLeaves
	 */
	private static void removeLoadedAdjacentNodes(Node node, Set<Integer> storedLeaves)
	{
		TIntArrayList list = new TIntArrayList(node.adjLeaves.toNativeArray());
		node.adjLeaves.clear();
		for (int nodeValue: list.toNativeArray()) {
			if (!storedLeaves.contains(Integer.valueOf(nodeValue))) {
				node.adjLeaves.add(nodeValue);
			}
		}
		
	}
	
	/**
	 * It add leafIndex of nodes that are not loaded but it is necessary update 
	 * index of vertices in triangles.
	 * @param node
	 * @param visitedNodes
	 * @param nodes4Update
	 * @param byteBuffer
	 * @param neighbours
	 */
	private static void addRequiredNodes4Update(OEMM.Node node, Set<Integer> visitedNodes,
			Set<Integer> nodes4Update, byte[] byteBuffer, int neighbours)
	{
		for (int i = 0; i < neighbours; i++) {
			Integer nodeNumber = Integer.valueOf(node.adjLeaves.get(byteBuffer[i]));
			if (!visitedNodes.contains(nodeNumber)) {
				nodes4Update.add(nodeNumber);
			}
		}
		
	}
	
	/**
	 * It updates labels of reindexed vertices in the triangle file of the non-loaded nodes.
	 * 
	 * @param oemm
	 * @param nodes4Update
	 * @param old2newIndex
	 */
	private static void updateNonReadNodes(OEMM oemm,
			Set<Integer> nodes4Update, Map<Integer, VertexIndexHolder> old2newIndex)
	{
		int[] leaf = new int[3];
		int[] localIndices = new int[3];
		
		ByteBuffer tbb = ByteBuffer.allocate(TRIANGLE_SIZE - 4);
		IntBuffer tib = tbb.asIntBuffer();
		tbb.limit(TRIANGLE_SIZE - 4);
		for (Integer nodeIndex: nodes4Update)
		{
			OEMM.Node node = oemm.leaves[nodeIndex.intValue()];
			FileChannel fc = null;
			try {
				fc = new RandomAccessFile(getTrianglesFile(oemm, node),"rw").getChannel();
				
				for (int i = 0; i < node.tn; i++)
				{
					boolean modified_triangle = false;
					final int filePosition = i * TRIANGLE_SIZE;
					fc.position(filePosition);
					tbb.rewind();
					fc.read(tbb);
					tib.rewind();
					
					tib.get(leaf);
					tib.get(localIndices);
					
					for (int ii = 0; ii < 3; ii++)
					{
						OEMM.Node oldNode = oemm.leaves[leaf[ii]];
						Integer globalIndexOfNode = Integer.valueOf(oldNode.minIndex + localIndices[ii]);
						if (!old2newIndex.containsKey(globalIndexOfNode)) {
							assert 0 <= localIndices[ii] && localIndices[ii] <= oldNode.vn;
							continue;
						}
						modified_triangle = true;
						
						VertexIndexHolder newIndex = old2newIndex.get(globalIndexOfNode);
						leaf[ii] = newIndex.getContainedNode().leafIndex;
						localIndices[ii] = newIndex.getLocalIndex();
						assert 0 <= localIndices[ii] && localIndices[ii] <= newIndex.containedNode.vn;
						
					}
					if (modified_triangle)
					{
						tib.rewind();
						tib.put(leaf);
						tib.put(localIndices);
						tbb.rewind();
						fc.position(filePosition);
						fc.write(tbb);
					}
				}
				
			} catch (FileNotFoundException e) {
				logger.error("Couldn't find file " + getTrianglesFile(oemm, node), e);
				throw new RuntimeException(e);
			} catch (IOException e) {
				logger.error("I/O error in operation with file " + getTrianglesFile(oemm, node), e);
				throw new RuntimeException(e);
			}
			try {
				
			} finally {
				try {
					fc.close();
				} catch (IOException e) {
					//Ignore this
				}
			}
		}
		
	}
	
	/**
	 * Get label of vertex before reindexing. It means old label whether was reindexed, 
	 * or present label otherwise.  
	 * @param new2oldIndex
	 * @param counter new label of vertex
	 * @return
	 */
	private static Integer getLaterIndex(Map<Integer, Integer> new2oldIndex, Integer counter)
	{
		if (new2oldIndex.containsKey(counter))
			return new2oldIndex.get(counter);
		return counter;
	}
	
	/**
	 * Creates map of leafIndex of adjacent node to local index. This is done 
	 * for specific node.
	 * @param node
	 * @return 
	 */
	private static Map<Integer, Byte> makeNodeIndex2adjIndexMap(Node node)
	{
		Map<Integer,Byte> nodeIndex2adjIndex = new HashMap<Integer, Byte>();
		for(int i = 0; i < node.adjLeaves.size(); i++) {
			nodeIndex2adjIndex.put(Integer.valueOf(node.adjLeaves.get(i)), Byte.valueOf((byte)(0xff & i)));
		}
		return nodeIndex2adjIndex;
	}

	/**
	 * Stores triangles of the mesh into oemm structure on the disk. 
	 *
	 * @param oemm
	 * @param mesh
	 * @param storedLeaves 
	 */
	private static void storeTriangles(OEMM oemm, Mesh mesh, Set<Integer> storedLeaves, Map<Vertex, Integer> mapVertexToLeafindex)
	{
		Map<Integer, List<Triangle>> mapLeafindexToTriangleList = new HashMap<Integer, List<Triangle>>();
		int[] leaf = new int[3];
		int[] pointIndex = new int[3];
		int[] positions = new int[3];
		for (Integer i: storedLeaves)
			mapLeafindexToTriangleList.put(i, new ArrayList<Triangle>());

		collectAllTriangles(oemm, mesh, mapVertexToLeafindex, mapLeafindexToTriangleList);
		for(Entry<Integer, List<Triangle>> entry: mapLeafindexToTriangleList.entrySet())
		{
			Node node = oemm.leaves[entry.getKey().intValue()];
			List<Triangle> triangleList = entry.getValue();
			
			DataOutputStream fc;
			try {
				fc = new DataOutputStream( new FileOutputStream(getTrianglesFile(oemm, node)));
			} catch (FileNotFoundException e1) {
				logger.error("I/O error when reading indexed file "+getTrianglesFile(oemm, node));
				e1.printStackTrace();
				throw new RuntimeException(e1);
			}
			try {
				
				for (Triangle triangle: triangleList) {
					triangle.setGroupId(node.leafIndex);
					for (int i = 0; i < 3; i++) {
						Node foundNode = oemm.leaves[searchNode(oemm, triangle.vertex[i], positions)];
						leaf[i] = foundNode.leafIndex;
						assert leaf[i] < oemm.leaves.length; 
						pointIndex[i] = triangle.vertex[i].getLabel() - foundNode.minIndex;
						assert pointIndex[i] < oemm.leaves[leaf[i]].vn; 
					}
					writeIntArray(fc, leaf);
					writeIntArray(fc, pointIndex);
					fc.writeInt(triangle.getGroupId());
				}
				node.tn = triangleList.size();
			
			} catch (IOException e) {
				logger.error("Error in saving to " + getTrianglesFile(oemm, node), e);
				e.printStackTrace();
				throw new RuntimeException(e);
			} finally {
				try {
					fc.close();
				} catch (IOException e) {
					//ignore this
				}
			}
		}
	}

	/**
	 * It browses all triangle of the mesh and it fills map of node index to list 
	 * of contained vertices.
	 * @param oemm
	 * @param mesh
	 * @param mapLeafindexToTriangleList
	 */
	private static void collectAllTriangles(OEMM oemm, Mesh mesh, Map<Vertex, Integer> mapVertexToLeafindex, Map<Integer, List<Triangle>> mapLeafindexToTriangleList)
	{
		int positions[] = new int[3];
		for(AbstractTriangle at: mesh.getTriangles())
		{
			Triangle tr = (Triangle) at;
			boolean hasOuterEdge = tr.vertex[0].getUV().length == 2 || tr.vertex[1].getUV().length == 2
			|| tr.vertex[2].getUV().length == 2;
			assert !hasOuterEdge && !tr.isOuter() || hasOuterEdge && tr.isOuter();
			if (tr.isOuter())
				continue;
			// By convention, if T=(V1,V2,V3) and each Vi is contained in node Ni,
			// then T belongs to min(Ni)
			int nodeNumber = Integer.MAX_VALUE;
			for(Vertex vert: tr.vertex)
			{
				int n;
				Integer InodeNumber = mapVertexToLeafindex.get(vert);
				if (InodeNumber != null)
					n = InodeNumber.intValue();
				else
					n = searchNode(oemm, vert, positions);
				if (n < nodeNumber)
					nodeNumber = n;
			}
			List<Triangle> triangles = mapLeafindexToTriangleList.get(Integer.valueOf(nodeNumber));
			if (triangles == null) {
				throw new UnsupportedOperationException("Cannot put triangle into octree node: " 
						+nodeNumber + ". Node is not loaded!");
			}
			triangles.add(tr);
		}
	}

	protected static File getAdjacencyFile(OEMM oemm, Node node)
	{
		return new File(oemm.getDirectory(), node.file+"a");
	}
	
	protected static File getTrianglesFile(OEMM oemm, Node node)
	{
		return new File(oemm.getDirectory(), node.file+"t");
	}

	protected static File getVerticesFile(OEMM oemm, OEMM.Node current)
	{
		return new File(oemm.getDirectory(), current.file+"v");
	}

	/**
	 * Read adjacency file and returns List of adjacent nodes without nodes
	 * that are loaded. 
	 * @param oemm  OEMM instance
	 * @param node  OEMM node
	 * @param visitedNodes  set of node indices being loaded
	 * @return a <code>List<List<Integer>></code> instance; for each vertex, returns indices of adjacent
	 *      and non-loaded nodes
	 */
	protected static List<List<Integer>> readAdjacencyFile(OEMM oemm, Node node, Set<Integer> visitedNodes)
	{
		List<List<Integer>> result = new ArrayList<List<Integer>>();
		List<Integer> nullList = new ArrayList<Integer>();
		DataInputStream dis = null;
		try {
			dis= new DataInputStream(new FileInputStream(getAdjacencyFile(oemm, node)));
			
			while (true)
			{
				int count;
				try {
					count = dis.readByte();
				} catch (EOFException e) {
					break;
				}
				if (count == 0)
					result.add(nullList);
				else
				{
					List<Integer> row = null;
					for (int i = 0; i < count; i++)
					{
						byte adjacentLeave = dis.readByte();
						Integer leafIndex = Integer.valueOf(node.adjLeaves.get(adjacentLeave));
						if (visitedNodes == null || !visitedNodes.contains(leafIndex))
						{
							if (row == null)
								row = new ArrayList<Integer>(count-i);
							row.add(leafIndex);
						}
					}
					if (row == null)
						row = nullList;
					result.add(row);
				}
			}
		} catch (FileNotFoundException e) {
			logger.error("Problem with opening " + getAdjacencyFile(oemm, node).getPath() + ". It may be new node.", e);
			//throw new RuntimeException(e);
		} catch (IOException e) {
			logger.error("Problem with reading " + getAdjacencyFile(oemm, node).getPath(), e);
			throw new RuntimeException(e);
		} finally {
			if (dis != null)
			{
				try {
					dis.close();
				} catch (IOException e) {
					///ignore this
				}
			}
		}
		
		return result;
	}
	
	private static void getFile(OEMM oemm, Node n, StringBuilder sb)
	{
		if (n.parent == null) {
			return;
		}
		getFile(oemm, n.parent, sb);
		int octant = -1;
		for (int i = 0; i < n.parent.child.length; i++) {
			if (n == n.parent.child[i]) {
				octant = i;
			}
		}
		assert octant != -1;
		sb.append(File.separatorChar).append(octant);
		if (!n.isLeaf) {
			new File(oemm.getDirectory() + File.separator + sb).mkdir();
		}
	}

	private static Node createNewNode(OEMM oemm, int[] positions)
	{
		Node n;
		logger.info("Creating new leaf node.");
		n = oemm.build(positions);
		n.adjLeaves = new TIntArrayList();
		n.leafIndex = oemm.leaves.length;
		Node prev = oemm.leaves[oemm.leaves.length - 1];
		n.minIndex = prev.minIndex - prev.getMaxIndex() + prev.minIndex - 1;
		n.maxIndex = n.minIndex - prev.getMaxIndex() + prev.minIndex;
		StringBuilder sb = new StringBuilder();
		getFile(oemm,n, sb);
		n.file = sb.toString();
		oemm.leaves = Arrays.copyOf(oemm.leaves, oemm.leaves.length + 1);
		oemm.leaves[n.leafIndex] = n;
		return n;
	}
	
	private static int searchNode(OEMM oemm, Vertex vert, int[] positions)
	{
		if (vert instanceof FakeNonReadVertex) {
			return ((FakeNonReadVertex) vert).getOEMMIndex();
		}
		double[] coords = vert.getUV();
		return searchNode(oemm, coords, positions);
	}

	private static int searchNode(OEMM oemm, double[] coords, int[] positions)
	{
		oemm.double2int(coords, positions);
		return oemm.search(positions).leafIndex;
	}
	
	private static void writeIntArray(DataOutputStream fc, int[] pointIndex) throws IOException
	{
		for(int val: pointIndex)
			fc.writeInt(val);
	}
	
	private static void writeDoubleArray(DataOutputStream fc, double[] uv) throws IOException
	{
		for (double val: uv)
			fc.writeDouble(val);
	}
	
	/**
	 * Class that helps to hold global index of vertex in local index way  - 
	 * <containing_node, local_index_in_node>
	 * @author koz01
	 *
	 */
	private static class VertexIndexHolder
	{
		private OEMM.Node containedNode;
		
		private int localIndex;
		
		public VertexIndexHolder(OEMM.Node node, int globalIndex) {
			assert node.minIndex <= globalIndex && globalIndex <= node.getMaxIndex();
			
			containedNode = node;
			localIndex = globalIndex - node.minIndex;
			assert localIndex >= 0 && localIndex < node.vn;
			
		}

		public OEMM.Node getContainedNode() {
			return containedNode;
		}

		public int getLocalIndex() {
			return localIndex;
		}
	}
	
}

/* jCAE stand for Java Computer Aided Engineering. Features are : Small CAD
   modeler, Finite element mesher, Plugin architecture.

    Copyright (C) 2006  EADS CRC

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

package org.jcae.mesh.amibe.ds;

import org.jcae.mesh.amibe.traits.HalfEdgeTraitsBuilder;
import org.jcae.mesh.amibe.metrics.Matrix3D;
import java.util.Collection;
import java.util.ArrayList;
import java.io.Serializable;
import org.apache.log4j.Logger;

public class HalfEdge extends AbstractHalfEdge implements Serializable
{
	private static Logger logger = Logger.getLogger(HalfEdge.class);
	private TriangleHE tri;
	private byte localNumber = 8;
	private int attributes = 8;
	// For non manifold edges, a virtual triangle is added
	//   Triangle(outerVertex, edge.origin(), edge.destination())
	// and sym points to an edge of this triangle.  It is said to
	// be outer.  The list of adjacent HalfEdge is stored in this
	// triangle, more specifically in sym.next.sym
	// This is very handy because all HalfEdge of non-outer triangles
	// can be considered as being manifold.
	// TODO: replace ArrayList by HalfEdge[]
	private Object sym = null;
	private HalfEdge next = null;

	private static final int [] next3 = { 1, 2, 0 };
	private static final int [] prev3 = { 2, 0, 1 };
	private static final double [][] temp = new double[4][3];
	
	public HalfEdge (HalfEdgeTraitsBuilder htb, TriangleHE tri, byte localNumber, byte attributes)
	{
		super(htb);
		this.tri = tri;
		this.localNumber = localNumber;
		this.attributes = attributes;
	}
	
	public void copy(HalfEdge src)
	{
		// Do not override tri!
		localNumber = src.localNumber;
		attributes = src.attributes;
		sym = src.sym;
	}
	
	/**
	 * Return the triangle tied to this object.
	 *
	 * @return the triangle tied to this object.
	 */
	@Override
	public final Triangle getTri()
	{
		return tri;
	}
	
	/**
	 * Return the edge local number.
	 *
	 * @return the edge local number.
	 */
	@Override
	public final int getLocalNumber()
	{
		return localNumber;
	}
	
	public final int getAttributes()
	{
		return attributes;
	}
	
	/**
	 * Set the edge tied to this object.
	 *
	 * @param e  the edge tied to this object.
	 */
	@Override
	public final void glue(AbstractHalfEdge e)
	{
		HEglue((HalfEdge) e);
	}
	private final void HEglue(HalfEdge s)
	{
		sym = s;
		if (s != null)
			s.sym = this;
	}
	
	public final HalfEdge notOriented()
	{
		assert sym instanceof HalfEdge;
		if (sym != null && sym.hashCode() < hashCode())
			return (HalfEdge) sym;
		return this;
	}
	
	/**
	 * Get the symmetric edge.
	 */
	@Override
	public final Object getAdj()
	{
		return sym;
	}

	/**
	 * Set the sym link.
	 */
	@Override
	public final void setAdj(Object e)
	{
		sym = e;
	}

	private final HalfEdge HEsym()
	{
		return (HalfEdge) sym;
	}

	@Override
	public final AbstractHalfEdge sym()
	{
		return (AbstractHalfEdge) sym;
	}

	@Override
	public final AbstractHalfEdge sym(AbstractHalfEdge that)
	{
		that = (AbstractHalfEdge) sym;
		return that;
	}

	/**
	 * Move to the next edge.
	 */
	@Override
	public final AbstractHalfEdge next()
	{
		return next;
	}
	
	@Override
	public final AbstractHalfEdge next(AbstractHalfEdge that)
	{
		that = next;
		return that;
	}
	
	/**
	 * Move to the previous edge.
	 */
	@Override
	public final AbstractHalfEdge prev()
	{
		return next.next;
	}
	
	@Override
	public final AbstractHalfEdge prev(AbstractHalfEdge that)
	{
		that = next.next;
		return that;
	}
	
	/**
	 * Move counterclockwise to the following edge with the same origin.
	 */
	@Override
	public final AbstractHalfEdge nextOrigin()
	{
		return next.next.sym();
	}
	
	@Override
	public final AbstractHalfEdge nextOrigin(AbstractHalfEdge that)
	{
		that = next.next.sym();
		return that;
	}
	
	/**
	 * Move counterclockwise to the previous edge with the same origin.
	 */
	public final AbstractHalfEdge prevOrigin()
	{
		return HEsym().next;
	}
	
	public final AbstractHalfEdge prevOrigin(AbstractHalfEdge that)
	{
		that = HEsym().next;
		return that;
	}
	
	/**
	 * Move counterclockwise to the following edge with the same
	 * destination.
	 */
	public final AbstractHalfEdge nextDest()
	{
		return HEsym().prev();
	}
	
	public final AbstractHalfEdge nextDest(AbstractHalfEdge that)
	{
		that = HEsym().prev();
		return that;
	}
	
	/**
	 * Move counterclockwise to the previous edge with the same
	 * destination.
	 */
	public final AbstractHalfEdge prevDest()
	{
		return next.sym();
	}
	
	public final AbstractHalfEdge prevDest(AbstractHalfEdge that)
	{
		that = next.sym();
		return that;
	}
	
	/**
	 * Move counterclockwise to the following edge with the same apex.
	 */
	public final AbstractHalfEdge nextApex()
	{
		return next.HEsym().next;
	}
	
	public final AbstractHalfEdge nextApex(AbstractHalfEdge that)
	{
		that = next.HEsym().next;
		return that;
	}
	
	/**
	 * Move clockwise to the previous edge with the same apex.
	 */
	public final AbstractHalfEdge prevApex()
	{
		return next.next.HEsym().prev();
	}
	
	public final AbstractHalfEdge prevApex(AbstractHalfEdge that)
	{
		that = next.next.HEsym().prev();
		return that;
	}
	
	//  The following 3 methods change the underlying triangle.
	//  So they also modify all HalfEdge bound to this one.
	/**
	 * Sets the start vertex of this edge.
	 *
	 * @param v  the start vertex of this edge.
	 */
	public final void setOrigin(Vertex v)
	{
		tri.vertex[next3[localNumber]] = v;
	}
	
	/**
	 * Sets the end vertex of this edge.
	 *
	 * @param v  the end vertex of this edge.
	 */
	public final void setDestination(Vertex v)
	{
		tri.vertex[prev3[localNumber]] = v;
	}
	
	/**
	 * Sets the apex of this edge.
	 *
	 * @param v  the apex of this edge.
	 */
	public final void setApex(Vertex v)
	{
		tri.vertex[localNumber] = v;
	}
	
	/**
	 * Set the next link.
	 */
	public final void setNext(HalfEdge e)
	{
		next = e;
	}
	
	/**
	 * Check if some attributes of this edge are set.
	 *
	 * @param attr  the attributes to check
	 * @return <code>true</code> if this HalfEdge has all these
	 * attributes set, <code>false</code> otherwise.
	 */
	@Override
	public final boolean hasAttributes(int attr)
	{
		return (attributes & attr) != 0;
	}
	
	/**
	 * Set attributes of this edge.
	 *
	 * @param attr  the attribute of this edge.
	 */
	@Override
	public final void setAttributes(int attr)
	{
		attributes |= attr;
	}
	
	/**
	 * Reset attributes of this edge.
	 *
	 * @param attr   the attributes of this edge to clear out.
	 */
	@Override
	public final void clearAttributes(int attr)
	{
		attributes &= ~attr;
	}
	
	/**
	 * Returns the start vertex of this edge.
	 *
	 * @return the start vertex of this edge.
	 */
	@Override
	public final Vertex origin()
	{
		return tri.vertex[next3[localNumber]];
	}
	
	/**
	 * Returns the end vertex of this edge.
	 *
	 * @return the end vertex of this edge.
	 */
	@Override
	public final Vertex destination()
	{
		return tri.vertex[prev3[localNumber]];
	}
	
	/**
	 * Returns the apex of this edge.
	 *
	 * @return the apex of this edge.
	 */
	@Override
	public final Vertex apex()
	{
		return tri.vertex[localNumber];
	}
	
	private static HalfEdge find(Vertex v1, Vertex v2)
	{
		HalfEdge ret = ((TriangleHE) v1.getLink()).getHalfEdge();
		if (ret == null)
			return null;
		if (ret.destination() == v1)
			ret = ret.next;
		else if (ret.apex() == v1)
			ret = ret.next.next;
		assert ret.origin() == v1 : v1+" not in "+ret.getTri();
		Vertex d = ret.destination();
		if (d == v2)
			return ret;
		do
		{
			ret = (HalfEdge) ret.nextOriginLoop();
			if (ret.destination() == v2)
				return ret;
		}
		while (ret.destination() != d);
		return null;
	}
	
	/**
	 * Move counterclockwise to the following edge with the same origin.
	 * If a boundary is reached, loop backward until another
	 * boundary is found and start again from there.
	 */
	@Override
	public final AbstractHalfEdge nextOriginLoop()
	{
		HalfEdge ret = this;
		if (ret.hasAttributes(OUTER) && ret.hasAttributes(BOUNDARY | NONMANIFOLD))
		{
			// Loop clockwise to another boundary
			// and start again from there.
			do
			{
				ret = (HalfEdge) ret.prevOrigin();
			}
			while (!ret.hasAttributes(OUTER));
		}
		else
			ret = (HalfEdge) ret.nextOrigin();
		return ret;
	}
	
	/**
	 * Move counterclockwise to the following edge with the same apex.
	 * If a boundary is reached, loop backward until another
	 * boundary is found and start again from there.
	 */
	public final HalfEdge nextApexLoop()
	{
		HalfEdge ret = this;
		if (ret.hasAttributes(OUTER) && ret.next.next.hasAttributes(BOUNDARY | NONMANIFOLD))
		{
			// Loop clockwise to another boundary
			// and start again from there.
			do
			{
				ret = (HalfEdge) ret.prevApex();
			}
			while (!ret.hasAttributes(AbstractHalfEdge.OUTER));
		}
		else
			ret = (HalfEdge) ret.nextApex();
		return ret;
	}
	
	/**
	 * Checks the dihedral angle of an edge.
	 * Warning: this method uses temp[0], temp[1], temp[2] and temp[3] temporary arrays.
	 *
	 * @param minCos  if the dot product of the normals to adjacent
	 *    triangles is lower than monCos, then <code>-1.0</code> is
	 *    returned.
	 * @return the minimum quality of the two trianglles generated
	 *    by swapping this edge.
	 */
	public final double checkSwap3D(double minCos)
	{
		double invalid = -1.0;
		// Check if there is an adjacent edge
		if (hasAttributes(OUTER | BOUNDARY | NONMANIFOLD))
			return invalid;
		// Check for coplanarity
		HalfEdge f = HEsym();
		Vertex o = origin();
		Vertex d = destination();
		Vertex a = apex();
		double s1 = Matrix3D.computeNormal3D(o.getUV(), d.getUV(), a.getUV(), temp[0], temp[1], temp[2]);
		double s2 = Matrix3D.computeNormal3D(f.tri.vertex[0].getUV(), f.tri.vertex[1].getUV(), f.tri.vertex[2].getUV(), temp[0], temp[1], temp[3]);
		if (Matrix3D.prodSca(temp[2], temp[3]) < minCos)
			return invalid;
		// Check for quality improvement
		Vertex n = f.apex();
		// Check for inverted triangles
		o.outer3D(n, a, temp[0]);
		double s3 = 0.5 * Matrix3D.prodSca(temp[2], temp[0]);
		if (s3 <= 0.0)
			return invalid;
		d.outer3D(a, n, temp[0]);
		double s4 = 0.5 * Matrix3D.prodSca(temp[2], temp[0]);
		if (s4 <= 0.0)
			return invalid;
		double p1 = o.distance3D(d) + d.distance3D(a) + a.distance3D(o);
		double p2 = d.distance3D(o) + o.distance3D(n) + n.distance3D(d);
		// No need to multiply by 12.0 * Math.sqrt(3.0)
		double Qbefore = Math.min(s1/p1/p1, s2/p2/p2);
		
		double p3 = o.distance3D(n) + n.distance3D(a) + a.distance3D(o);
		double p4 = d.distance3D(a) + a.distance3D(n) + n.distance3D(d);
		double Qafter = Math.min(s3/p3/p3, s4/p4/p4);
		if (Qafter > Qbefore)
			return Qafter;
		return invalid;
	}
	
	/**
	 * Swaps an edge.
	 *
	 * This routine swaps an edge (od) to (na).  (on) is returned
	 * instead of (na), because this helps turning around o, eg.
	 * at the end of {@link org.jcae.mesh.amibe.patch.VirtualHalfEdge2D#split3}.
	 *
	 *          d                    d
	 *          .                    .
	 *         /|\                  / \
	 *        / | \                /   \
	 *       /  |  \              /     \
	 *    a +   |   + n  ---&gt;  a +-------+ n
	 *       \  |  /              \     /
	 *        \ | /                \   /
	 *         \|/                  \ /
	 *          '                    '
	 *          o                    o
	 */
	@Override
	public final AbstractHalfEdge swap()
	{
		return HEswap();
	}
	private final HalfEdge HEswap()
	{
		Vertex o = origin();
		Vertex d = destination();
		Vertex a = apex();
		/*
		 *            d                    d
		 *            .                    .
		 *           /|\                  / \
		 *       s0 / | \ s3         s0  /   \ s3
		 *         /  |  \              / T2  \
		 *      a + T1|T2 + n  --->  a +-------+ n
		 *         \  |  /              \ T1  /
		 *       s1 \ | / s2         s1  \   / s2
		 *           \|/                  \ /
		 *            '                    '
		 *            o                    o
		 */
		// T1 = (oda)  --> (ona)
		// T2 = (don)  --> (dan)
		assert !hasAttributes(OUTER | BOUNDARY | NONMANIFOLD) : this;
		HalfEdge [] e = new HalfEdge[6];
		e[0] = next;
		e[1] = next.next;
		e[2] = HEsym().next;
		e[3] = HEsym().next.next;
		e[4] = this;
		e[5] = HEsym();
		//  Clear SWAPPED flag for all edges of the 2 triangles
		for (int i = 0; i < 6; i++)
		{
			e[i].clearAttributes(AbstractHalfEdge.SWAPPED);
			e[i].HEsym().clearAttributes(AbstractHalfEdge.SWAPPED);
		}
		//  Adjust vertices
		Vertex n = e[5].apex();
		e[4].setDestination(n);           // (ona)
		e[5].setDestination(a);           // (dan)
		//  Adjust edge informations
		//    T1: e[1] is unchanged
		TriangleHE T1 = e[1].tri;
		e[1].next = e[2];
		e[2].next = e[4];
		e[4].next = e[1];
		e[2].tri = e[4].tri = T1;
		e[2].localNumber = (byte) next3[e[1].localNumber];
		e[4].localNumber = (byte) prev3[e[1].localNumber];
		//    T2: e[3] is unchanged
		TriangleHE T2 = e[3].tri;
		e[3].next = e[0];
		e[0].next = e[5];
		e[5].next = e[3];
		e[0].tri = e[5].tri = T2;
		e[0].localNumber = (byte) next3[e[3].localNumber];
		e[5].localNumber = (byte) prev3[e[3].localNumber];
		//  Adjust edge pointers of triangles
		if (e[1].localNumber == 1)
			T1.setHalfEdge(e[4]);
		else if (e[1].localNumber == 2)
			T1.setHalfEdge(e[2]);
		if (e[3].localNumber == 1)
			T2.setHalfEdge(e[5]);
		else if (e[3].localNumber == 2)
			T2.setHalfEdge(e[0]);
		//  Mark new edges
		e[4].attributes = 0;
		e[5].attributes = 0;
		e[4].setAttributes(AbstractHalfEdge.SWAPPED);
		e[5].setAttributes(AbstractHalfEdge.SWAPPED);
		//  Fix links to triangles
		o.setLink(T1);
		d.setLink(T2);
		// Be consistent with AbstractHalfEdge.swap()
		return e[2];
	}
	
	/**
	 * Return the area of this triangle.
	 * @return the area of this triangle.
	 * Warning: this method uses temp[0], temp[1] and temp[2] temporary arrays.
	 */
	@Override
	public double area()
	{
		double [] p0 = origin().getUV();
		double [] p1 = destination().getUV();
		double [] p2 = apex().getUV();
		temp[1][0] = p1[0] - p0[0];
		temp[1][1] = p1[1] - p0[1];
		temp[1][2] = p1[2] - p0[2];
		temp[2][0] = p2[0] - p0[0];
		temp[2][1] = p2[1] - p0[1];
		temp[2][2] = p2[2] - p0[2];
		Matrix3D.prodVect3D(temp[1], temp[2], temp[0]);
		return 0.5 * Matrix3D.norm(temp[0]);
	}
	
	/**
	 * Checks that triangles are not inverted if origin vertex is moved.
	 *
	 * @param newpt  the new position to be checked.
	 * @return <code>false</code> if the new position produces
	 *    an inverted triangle, <code>true</code> otherwise.
	 * Warning: this method uses temp[0], temp[1], temp[2] and temp[3] temporary arrays.
	 */
	@Override
	public final boolean checkNewRingNormals(double [] newpt)
	{
		//  Loop around apex to check that triangles will not be inverted
		Vertex d = destination();
		HalfEdge f = next;
		do
		{
			if (f.hasAttributes(OUTER))
			{
				f = f.nextApexLoop();
				continue;
			}
			if (f.origin().getLink() instanceof Triangle[])
				return false;
			double area  = Matrix3D.computeNormal3DT(f.origin().getUV(), f.destination().getUV(), f.apex().getUV(), temp[0], temp[1], temp[2]);
			double [] x1 = f.origin().getUV();
			for (int i = 0; i < 3; i++)
				temp[3][i] = newpt[i] - x1[i];
			if (Matrix3D.prodSca(temp[3], temp[2]) >= - area)
				return false;
			f = f.nextApexLoop();
		}
		while (f.origin() != d);
		return true;
	}
	
	/**
	 * Check whether an edge can be contracted.
	 *
	 * @param n the resulting vertex
	 * @return <code>true</code> if this edge can be contracted into the single vertex n, <code>false</code> otherwise.
	 */
	@Override
	public final boolean canCollapse(AbstractVertex n)
	{
		if (!checkInversion((Vertex) n))
			return false;
		
		//  Topology check
		//  TODO: normally this check could be removed, but the
		//        following test triggers an error:
		//    * mesh Scie_shell.brep with deflexion=0.2 aboslute
		//    * decimate with length=6
		Collection<Vertex> link = origin().getNeighboursNodes();
		link.retainAll(destination().getNeighboursNodes());
		return link.size() < 3;
	}
	
	private final boolean checkInversion(Vertex n)
	{
		Vertex o = origin();
		Vertex d = destination();
		Vertex a = apex();
		//  If both vertices are non-manifold, do not contract
		//  TODO: allow contracting non-manifold edges
		if (o.getLink() instanceof Triangle[] && d.getLink() instanceof Triangle[])
			return false;
		//  If both adjacent edges are on a boundary, do not contract
		if (next.hasAttributes(BOUNDARY | NONMANIFOLD) && next.next.hasAttributes(BOUNDARY | NONMANIFOLD))
			return false;
		if (HEsym().next.hasAttributes(BOUNDARY | NONMANIFOLD) && HEsym().next.next.hasAttributes(BOUNDARY | NONMANIFOLD))
			return false;
		//  Loop around o to check that triangles will not be inverted
		HalfEdge f = next;
		HalfEdge g = HEsym();
		double [] xn = n.getUV();
		do
		{
			//  TODO: allow contracting edges when a vertex is non manifold
			if (f.origin().getLink() instanceof Triangle[])
				return false;
			if (f.tri != tri && f.tri != g.tri && !f.hasAttributes(OUTER))
			{
				double area  = Matrix3D.computeNormal3DT(f.origin().getUV(), f.destination().getUV(), f.apex().getUV(), temp[0], temp[1], temp[2]);
				double [] x1 = f.origin().getUV();
				for (int i = 0; i < 3; i++)
					temp[3][i] = xn[i] - x1[i];
				// Two triangles are removed when an edge is contracted.
				// So normally triangle areas should increase.  If they
				// decrease significantly, there may be a problem.
				if (Matrix3D.prodSca(temp[3], temp[2]) >= - area)
					return false;
			}
			f = f.nextApexLoop();
		}
		while (f.origin() != d);
		//  Loop around d to check that triangles will not be inverted
		f = next.next;
		do
		{
			//  TODO: allow contracting edges when a vertex is non manifold
			if (f.origin().getLink() instanceof Triangle[])
				return false;
			if (f.tri != tri && f.tri != g.tri && !f.hasAttributes(OUTER))
			{
				double area  = Matrix3D.computeNormal3DT(f.origin().getUV(), f.destination().getUV(), f.apex().getUV(), temp[0], temp[1], temp[2]);
				double [] x1 = f.origin().getUV();
				for (int i = 0; i < 3; i++)
					temp[3][i] = xn[i] - x1[i];
				if (Matrix3D.prodSca(temp[3], temp[2]) >= - area)
					return false;
			}
			f = f.nextApexLoop();
		}
		while (f.origin() != a);
		return true;
	}
	
	/**
	 * Contract an edge.
	 * TODO: Attributes are not checked.
	 * @param n the resulting vertex
	 */
	@Override
	public final AbstractHalfEdge collapse(AbstractMesh m, AbstractVertex n)
	{
		return HEcollapse((Mesh) m, (Vertex) n);
	}
	private HalfEdge HEcollapse(Mesh m, Vertex n)
	{
		Vertex o = origin();
		Vertex d = destination();
		assert apex() != m.outerVertex : "Cannot contract "+this;
		assert o.isWritable() && d.isWritable(): "Cannot contract "+this;
		if (logger.isDebugEnabled())
			logger.debug("contract ("+o+" "+d+")\ninto "+n);
		/*
		 *           V1                       V1
		 *  V3+-------+-------+ V4   V3 +------+------+ V4
		 *     \ t3  / \ t4  /           \  t3 | t4  /
		 *      \   /   \   /              \   |   /
		 *       \ / t1  \ /                 \ | /
		 *      o +-------+ d   ------>      n +
		 *       / \ t2  / \                 / | \
		 *      /   \   /   \              /   |   \
		 *     / t5  \ / t6  \           /  t5 | t6  \
		 *    +-------+-------+         +------+------+
		 *  V5        V2       V6     V5       V2      V6
		 */
		// this = (odV1)
		
		//  Replace o by n in all incident triangles
		HalfEdge e, f, s;
		e = this;
		do
		{
			e.setOrigin(n);
			e = (HalfEdge) e.nextOriginLoop();
		}
		while (e.destination() != d);
		//  Replace d by n in all incident triangles
		e = e.HEsym();
		do
		{
			e.setOrigin(n);
			e = (HalfEdge) e.nextOriginLoop();
		}
		while (e.destination() != n);
		//  Update adjacency links.  For clarity, o and d are
		//  written instead of n.
		e = next;               // (dV1o)
		int attr4 = e.attributes;
		s = e.HEsym();          // (V1dV4)
		e = e.next;             // (V1od)
		int attr3 = e.attributes;
		f = e.HEsym();          // (oV1V3)
		if (f != null)
			f.HEglue(s);
		else if (s != null)
			s.HEglue(null);
		if (f != null)
			f.attributes |= attr4;
		if (s != null)
			s.attributes |= attr3;
		if (!hasAttributes(AbstractHalfEdge.OUTER))
		{
			TriangleHE t34 = f.tri;
			if (t34.isOuter())
				t34 = s.tri;
			assert !t34.isOuter() : s+"\n"+f;
			f.destination().setLink(t34);
			n.setLink(t34);
		}
		e = e.next;                     // (odV1)
		e = e.HEsym();                  // (doV2)
		e = e.next;                     // (oV2d)
		int attr5 = e.attributes;
		s = e.HEsym();                  // (V2oV5)
		e = e.next;                     // (V2do)
		int attr6 = e.attributes;
		f = e.HEsym();                  // (dV2V6)
		if (f != null)
			f.HEglue(s);
		else if (s != null)
			s.HEglue(null);
		if (f != null)
			f.attributes |= attr5;
		if (s != null)
			s.attributes |= attr6;
		if (!e.hasAttributes(AbstractHalfEdge.OUTER))
		{
			TriangleHE t56 = s.tri;
			if (t56.isOuter())
				t56 = f.tri;
			assert !t56.isOuter();
			s.origin().setLink(t56);
			n.setLink(t56);
		}
		e = e.next;                     // (doV2)
		// Must be called before T2 is removed
		s = e.HEsym();                  // (odV1)
		// Remove T2
		e.clearAttributes(AbstractHalfEdge.MARKED);
		m.remove(e.tri);
		// Must be called before T1 is removed
		e = s.next.HEsym().HEsym();    // (oV1V3)
		// Remove T1
		s.clearAttributes(AbstractHalfEdge.MARKED);
		m.remove(s.tri);
		// Check that all o and d instances have been removed
		// This is costful, it is disabled by default but may
		// be enabled when debugging.
		/*
		boolean checkVertices = false;
		assert checkVertices = true;
		if (checkVertices)
		{
			for (AbstractTriangle at: m.getTriangles())
			{
				Triangle t = (Triangle) at;
				assert t.vertex[0] != o && t.vertex[1] != o && t.vertex[2] != o : "Vertex "+o+" found in "+t;
				assert t.vertex[0] != d && t.vertex[1] != d && t.vertex[2] != d : "Vertex "+d+" found in "+t;
			}
		}
		*/
		// By convention, edge is moved into (dV4V1), but this may change.
		// This is why V1 cannot be m.outerVertex, otherwise we cannot
		// ensure that return HalfEdge is (oV1V3)
		return e;
	}
	
	/**
	 * Split an edge.  This is the opposite of contract.
	 *
	 * @param n the resulting vertex
	 */
	@Override
	public final AbstractHalfEdge split(AbstractMesh m, AbstractVertex n)
	{
		HEsplit((Mesh) m, (Vertex) n);
		return this;
	}
	private final void HEsplit(Mesh m, Vertex n)
	{
		/*
		 *            V1                             V1
		 *            /'\                            /|\
		 *          /     \                        /  |  \
		 *        /      h1 \                    /    |    \
		 *      /             \                /    n1|   h1 \
		 *    /       t1        \            /   t1   |  t3    \
		 * o +-------------------+ d ---> o +---------+---------+ d
		 *    \       t2        /            \   t2   |  t4    /
		 *      \             /                \    n2|   h2 /
		 *        \      h2 /                    \    |    /
		 *          \     /                        \  |  /
		 *            \,/                            \|/
		 *            V2                             V2
		 */
		HalfEdge h1 = next;             // (dV1o)
		HalfEdge h2 = HEsym().next.next;// (v2do)
		TriangleHE t1 = h1.tri;
		TriangleHE t2 = h2.tri;
		TriangleHE t3 = (TriangleHE) m.factory.createTriangle(t1);
		TriangleHE t4 = (TriangleHE) m.factory.createTriangle(t2);
		m.add(t3);
		m.add(t4);
		
		// (dV1) is not modified by this operation, so we move
		// h1 into t3 so that it does not need to be updated by
		// the caller.
		HalfEdge n1 = t3.getHalfEdge();
		for (int i = h1.localNumber; i > 0; i--)
			n1 = n1.next;
		// Update forward links
		HalfEdge h1next = h1.next;
		h1.next = n1.next;
		h1.next.next.next = h1;
		n1.next = h1next;
		n1.next.next.next = n1;
		if (t1.getHalfEdge() == h1)
		{
			t1.setHalfEdge(n1);
			t3.setHalfEdge(h1);
		}
		// Update Triangle links
		n1.tri = t1;
		h1.tri = t3;
		// (dV2) is not modified by this operation, so we move
		// h2 into t4 so that it does not need to be updated by
		// the caller.
		HalfEdge n2 = t4.getHalfEdge();
		for (int i = h2.localNumber; i > 0; i--)
			n2 = n2.next;
		// Update links
		HalfEdge h2next = h2.next;
		h2.next = n2.next;
		h2.next.next.next = h2;
		n2.next = h2next;
		n2.next.next.next = n2;
		if (t2.getHalfEdge() == h2)
		{
			t2.setHalfEdge(n2);
			t4.setHalfEdge(h2);
		}
		// Update Triangle links
		n2.tri = t2;
		h2.tri = t4;

		// Update vertices
		n1.setOrigin(n);
		n2.setDestination(n);
		h1.setApex(n);
		h2.setApex(n);
		if (t1.isOuter())
		{
			n.setLink(t2);
			h1.origin().setLink(t4);
		}
		else
		{
			n.setLink(t1);
			h1.origin().setLink(t3);
		}

		h1.next.HEglue(n1);
		h2.next.next.HEglue(n2);
		h2.next.HEglue(h1.next.next);
		n2.next.HEglue(n1.next.next);

		// Clear BOUNDARY and NONMANIFOLD flags on inner edges
		h1.next.clearAttributes(BOUNDARY | NONMANIFOLD);
		n1.clearAttributes(BOUNDARY | NONMANIFOLD);
		h2.next.next.clearAttributes(BOUNDARY | NONMANIFOLD);
		n2.clearAttributes(BOUNDARY | NONMANIFOLD);
	}
	
	@Override
	public String toString()
	{
		StringBuilder r = new StringBuilder();
		r.append("hashCode: "+hashCode());
		r.append("\nTriangle: "+tri.hashCode());
		r.append("\nLocal number: "+localNumber);
		if (sym != null)
		{
			if (sym instanceof HalfEdge)
			{
				HalfEdge e = (HalfEdge) sym;
				r.append("\nSym: "+e.tri.hashCode()+"["+e.localNumber+"]");
			}
			else
			{
				ArrayList<HalfEdge> list = (ArrayList<HalfEdge>) sym;
				r.append("\nSym: [");
				for (HalfEdge e: list)
				{
					r.append(e.tri.hashCode()+"["+e.localNumber+"]");
				}
				r.append("]");
			}
		}
		r.append("\nAttributes: "+Integer.toHexString(attributes));
		r.append("\nVertices:");
		r.append("\n  Origin: "+origin());
		r.append("\n  Destination: "+destination());
		r.append("\n  Apex: "+apex());
		return r.toString();
	}

	private static void unitTestBuildMesh(Mesh m, Vertex [] v)
	{
		/*
		 *                       v2
		 *                       +
		 *  Initial            / |
		 *  triangulation    /   |
		 *                 /     |
		 *               /       |
		 *             +---------+
		 *             v0        v1
		 *
		 * Final result:
		 *  v4        v3        v2
		 *   +---------+---------+
		 *   | \       |       / |
		 *   |   \     |     /   |
		 *   |     \   |   /     |
		 *   |       \ | /       |
		 *   +---------+---------+
		 *   v5        v0       v1
		 */
		logger.info("Building mesh...");
		TriangleHE T = (TriangleHE) m.factory.createTriangle(v[0], v[1], v[2]);
		v[0].setLink(T);
		v[1].setLink(T);
		v[2].setLink(T);
		m.add(T);
		Vertex [] init = new Vertex[3];
		System.arraycopy(v, 0, init, 0, 3);
		m.buildAdjacency(init, -1.0);
		assert m.isValid();
		HalfEdge e = T.getHalfEdge().next; // (v2,v0,v1)
		e.HEsplit(m, v[3]); // (v2,v3,v1)
		assert m.isValid();
		e = e.next;         // (v3,v1,v2)
		e = e.HEswap();     // (v3,v0,v2)
		assert m.isValid();
		/*
		 *            v3        v2
		 *             +---------+
		 *             |       / |
		 *             |     /   |
		 *             |   /     |
		 *             | /       |
		 *             +---------+
		 *             v0       v1
		 */
		e.HEsplit(m, v[5]); // (v3,v5,v2)
		assert m.isValid();
		e = e.next;         // (v5,v2,v3)
		e = e.HEswap();     // (v5,v0,v3)
		assert m.isValid();
		/*
		 *            v3        v2
		 *             +---------+
		 *           / |       / |
		 *         /   |     /   |
		 *       /     |   /     |
		 *     /       | /       |
		 *   +---------+---------+
		 *   v5        v0       v1
		 */
		e = e.next.next;    // (v3,v5,v0)
		e.HEsplit(m, v[4]); // (v3,v4,v0)
		assert m.isValid();
		/*
		 *  v4        v3        v2
		 *   +---------+---------+
		 *   | \       |       / |
		 *   |   \     |     /   |
		 *   |     \   |   /     |
		 *   |       \ | /       |
		 *   +---------+---------+
		 *   v5        v0       v1
		 */
	}
	
	private static void unitTestCheckLoopOrigin(Mesh m, Vertex o, Vertex d)
	{
		HalfEdge e = HalfEdge.find(o, d);
		if (e == null)
		{
		        logger.fatal("find(o, d) failed, aborting!");
		        System.exit(-1);
		}
		logger.info("Loop around origin: "+o);
		logger.info(" first destination: "+d);
		int cnt = 0;
		int expected = 4;
		do
		{
			e = (HalfEdge) e.nextOriginLoop();
			cnt++;
		}
		while (e.destination() != d);
		if (cnt != expected)
		{
		        logger.fatal("Failed test: LoopOrigin cnt != "+expected+": "+o+" "+d);
		        System.exit(-1);
		}
	}
	
	private static void unitTestCheckContract(Mesh m, Vertex o, Vertex d, Vertex n)
	{
		HalfEdge e = HalfEdge.find(o, d);
		if (e == null)
		        System.exit(-1);
		e.HEcollapse(m, n);
		assert m.isValid();
	}
	
	public static void main(String args[])
	{
		Mesh m = new Mesh();
		Vertex [] v = new Vertex[6];
		v[0] = (Vertex) m.factory.createVertex(0.0, 0.0, 0.0);
		v[1] = (Vertex) m.factory.createVertex(1.0, 0.0, 0.0);
		v[2] = (Vertex) m.factory.createVertex(1.0, 1.0, 0.0);
		v[3] = (Vertex) m.factory.createVertex(0.0, 1.0, 0.0);
		v[4] = (Vertex) m.factory.createVertex(-1.0, 1.0, 0.0);
		v[5] = (Vertex) m.factory.createVertex(-1.0, 0.0, 0.0);
		unitTestBuildMesh(m, v);
		assert m.isValid();
		logger.info("Checking loops...");
		unitTestCheckLoopOrigin(m, v[3], v[4]);
		unitTestCheckLoopOrigin(m, v[3], v[2]);
		unitTestCheckLoopOrigin(m, v[3], m.outerVertex);
		unitTestCheckContract(m, v[0], v[1], v[0]);
		unitTestCheckContract(m, v[5], v[0], v[0]);
		unitTestCheckContract(m, v[4], v[0], v[0]);
		assert m.isValid();
		logger.info("Done.");

		logger.info("Building non-manifold mesh...");
		m = new Mesh();
		Vertex [] nmv = new Vertex[6];
		nmv[0] = (Vertex) m.factory.createVertex(0.0, 0.0, 0.0);
		nmv[1] = (Vertex) m.factory.createVertex(0.0, 0.0, 3.0);
		nmv[2] = (Vertex) m.factory.createVertex(1.0, 0.0, 0.0);
		nmv[3] = (Vertex) m.factory.createVertex(0.0, 1.0, 0.0);
		nmv[4] = (Vertex) m.factory.createVertex(-1.0, 0.0, 0.0);
		nmv[5] = (Vertex) m.factory.createVertex(0.0, -1.0, 0.0);
		for (int i = 2; i < 6; i++)
		{
			TriangleHE T = (TriangleHE) m.factory.createTriangle(nmv[0], nmv[1], nmv[i]);
			nmv[i].setLink(T);
			if (i == 2)
			{
				nmv[0].setLink(T);
				nmv[1].setLink(T);
			}
			m.add(T);
		}
		m.buildAdjacency(nmv, -1.0);
		assert m.isValid();

		m = new Mesh();
		unitTestBuildMesh(m, v);
		HalfEdge e = HalfEdge.find(v[0], v[4]);
		e.HEswap();
		assert m.isValid();
		java.util.HashMap<String, String> opts = new java.util.HashMap<String, String>();
		opts.put("size", "0.1");
		new org.jcae.mesh.amibe.algos3d.DecimateHalfEdge(m, opts).compute();
	}
}

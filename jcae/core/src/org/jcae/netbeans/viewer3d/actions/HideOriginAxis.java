/*
 * Project Info:  http://jcae.sourceforge.net
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 *
 * (C) Copyright 2004, by EADS France
 */

package org.jcae.netbeans.viewer3d.actions;

import java.awt.event.ActionEvent;
import org.jcae.netbeans.viewer3d.CurrentViewChangeListener;
import org.jcae.netbeans.viewer3d.ViewManager;
import org.jcae.vtk.View;
import org.openide.util.HelpCtx;
import org.openide.util.actions.BooleanStateAction;

/**
 * @author Jerome Robert
 *
 */
public class HideOriginAxis extends BooleanStateAction implements CurrentViewChangeListener
{
	HideOriginAxis()
	{
		super();
		ViewManager.getDefault().addViewListener(this);
		setBooleanState(false);
	}
	
	/* (non-Javadoc)
	 * @see org.openide.util.actions.BooleanStateAction#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent arg0)
	{
    	View v=ViewManager.getDefault().getCurrentView();
		
    	if(v!=null)
		{
    		v.getCameraManager().setOriginAxisVisible(!v.getCameraManager().isOriginAxisVisible());
			setBooleanState(!v.getCameraManager().isRelativeAxisVisible());
		}
	}
	
	public void currentViewChanged(View view)
	{
		setBooleanState(!view.getCameraManager().isOriginAxisVisible());
	}
	
	/* (non-Javadoc)
	 * @see org.openide.util.actions.SystemAction#getName()
	 */
	public String getName()
	{
		return "Hide axis";
	}
	
	/* (non-Javadoc)
	 * @see org.openide.util.actions.SystemAction#getHelpCtx()
	 */
	public HelpCtx getHelpCtx()
	{
        return HelpCtx.DEFAULT_HELP;
        // If you will provide context help then use:
        // return new HelpCtx(MyAction.class);
	}
    
	protected String iconResource()
    {
        return "org/jcae/netbeans/viewer3d/actions/hideaxis.gif";
    }
}

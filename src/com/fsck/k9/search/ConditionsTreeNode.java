package com.fsck.k9.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;

import com.fsck.k9.search.SearchSpecification.Attribute;
import com.fsck.k9.search.SearchSpecification.Searchfield;
import com.fsck.k9.search.SearchSpecification.SearchCondition;

/**
 * This class will be used to store search conditions. It's a
 * basic boolean expression binary tree. The output will be SQL queries.
 *
 * TODO removing stuff
 *
 * @author dzan
 */
public class ConditionsTreeNode implements Parcelable {

    public enum Operator {
        AND, OR, NOT, CONDITION;
    }

    public ConditionsTreeNode mLeft;
    public ConditionsTreeNode mRight;
    public ConditionsTreeNode mParent;

    public Operator mValue;
    public SearchCondition mCondition;

    public int mLeftMPTTMarker;
    public int mRightMPTTMarker;

    /* ************************************************************
     *  Static Helpers to restore a tree
     * ************************************************************/
    public static ConditionsTreeNode buildTreeFromDB(Cursor cursor) {
        Stack<ConditionsTreeNode> stack = new Stack<ConditionsTreeNode>();
        ConditionsTreeNode tmp = null;

        // root node
        if (cursor.moveToFirst()) {
            tmp = buildNodeFromRow(cursor);
            stack.push(tmp);
        }

        // other nodes
        while (cursor.moveToNext()) {
            tmp = buildNodeFromRow(cursor);
            if (tmp.mRightMPTTMarker < stack.peek().mRightMPTTMarker) {
                stack.peek().mLeft = tmp;
                stack.push(tmp);
            } else {
                while (stack.peek().mRightMPTTMarker < tmp.mRightMPTTMarker) {
                    stack.pop();
                }
                stack.peek().mRight = tmp;
            }
        }
        return tmp;
    }

    private static ConditionsTreeNode buildNodeFromRow(Cursor cursor) {
        ConditionsTreeNode result = null;
        SearchCondition condition = null;

        Operator tmpValue = ConditionsTreeNode.Operator.valueOf(cursor.getString(5));

        if (tmpValue == Operator.CONDITION) {
            condition = new SearchCondition(Searchfield.valueOf(cursor.getString(0)),
                    Attribute.valueOf(cursor.getString(2)), cursor.getString(1));
        }

        result = new ConditionsTreeNode(condition);
        result.mValue = tmpValue;
        result.mLeftMPTTMarker = cursor.getInt(3);
        result.mRightMPTTMarker = cursor.getInt(4);

        return result;
    }
    /* ************************************************************
     *  Constructors
     * ************************************************************/
    public ConditionsTreeNode(SearchCondition condition) {
        mParent = null;
        mCondition = condition;
        mValue = Operator.CONDITION;
    }

    public ConditionsTreeNode(ConditionsTreeNode parent, Operator op) {
        mParent = parent;
        mValue = op;
        mCondition = null;
    }

    /* ************************************************************
     *  Public Modifiers
     * ************************************************************/

    /**
     * Adds the expression as the second argument of an AND
     * clause to this node.
     *
     * @param expr Expression to 'AND' with.
     * @return New top AND node.
     * @throws Exception
     */
    public ConditionsTreeNode and(ConditionsTreeNode expr) throws Exception {
        return add(expr, Operator.AND);
    }

    /**
     * Adds the expression as the second argument of an OR
     * clause to this node.
     *
     * @param expr Expression to 'OR' with.
     * @return New top OR node.
     * @throws Exception
     */
    public ConditionsTreeNode or(ConditionsTreeNode expr) throws Exception {
        return add(expr, Operator.OR);
    }

    /* ************************************************************
     *  Public Access
     * ************************************************************/
    public SearchCondition getCondition() {
        return mCondition;
    }

    /* ************************************************************
     *  Private Logic Methods
     * ************************************************************/
    private ConditionsTreeNode add(ConditionsTreeNode expr, Operator op) throws Exception{
        ConditionsTreeNode tmpNode = new ConditionsTreeNode(mParent, op);
        tmpNode.mLeft = this;
        tmpNode.mRight = expr;

        if (mParent != null) {
            mParent.updateChild(this, tmpNode);
        }
        this.mParent = tmpNode;

        if (expr.mParent != null) {
            throw new Exception("Can only add new expressions from root node down.");
        }
        expr.mParent = tmpNode;

        return tmpNode;
    }

    private void updateChild(ConditionsTreeNode oldChild, ConditionsTreeNode newChild) {
        // we can compare objects because this is the desired behaviour in this case
        if (mLeft == oldChild) {
            mLeft = newChild;
        } else if (mRight == oldChild) {
            mRight = newChild;
        }
    }

    /* ********************************************************************
     *  Writing out the tree in various forms
     *
     *  To have a valid SQL query we basically just run over
     *  the tree in-order.
     * ********************************************************************/
    @Override
    public String toString() {
        return " " + (mLeft == null ? "" : mLeft)
                + " " + ( mCondition == null ? mValue.name() : mCondition )
                + (mRight == null ? "" : mRight) ;
    }

    public String toDbString() {
        return " " + (mLeft == null ? "" : mLeft)
                + " " + ( mCondition == null ? mValue.name() : mCondition )
                + (mRight == null ? "" : mRight) ;
    }

    /* ********************************************************************
     *  Parcelable
     * ********************************************************************/
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mValue.ordinal());
        dest.writeParcelable(mCondition, flags);
        dest.writeParcelable(mLeft, flags);
        dest.writeParcelable(mRight, flags);
    }

    public static final Parcelable.Creator<ConditionsTreeNode> CREATOR =
            new Parcelable.Creator<ConditionsTreeNode>() {

        @Override
        public ConditionsTreeNode createFromParcel(Parcel in) {
            return new ConditionsTreeNode(in);
        }

        @Override
        public ConditionsTreeNode[] newArray(int size) {
            return new ConditionsTreeNode[size];
        }
    };

    private ConditionsTreeNode(Parcel in) {
        mValue = Operator.values()[in.readInt()];
        mCondition = in.readParcelable(ConditionsTreeNode.class.getClassLoader());
        mLeft = in.readParcelable(ConditionsTreeNode.class.getClassLoader());
        mRight = in.readParcelable(ConditionsTreeNode.class.getClassLoader());
        mParent = null;
        if (mLeft != null) {
            mLeft.mParent = this;
        }
        if (mRight != null) {
            mRight.mParent = this;
        }
    }

    /**
     * Get a set of all the leaves in the tree.
     * @return Set of all the leaves.
     */
    public HashSet<ConditionsTreeNode> getLeafSet() {
        HashSet<ConditionsTreeNode> leafSet = new HashSet<ConditionsTreeNode>();
        return getLeafSet(leafSet);
    }

    private HashSet<ConditionsTreeNode> getLeafSet(HashSet<ConditionsTreeNode> leafSet) {
        if (mLeft == null && mRight == null) {
            leafSet.add(this);
            return leafSet;
        } else {
            mLeft.getLeafSet(leafSet);
            mRight.getLeafSet(leafSet);
            return leafSet;
        }
    }

    /* ********************************************************************
     *  Applying the labels in an mptt order
     *  http://www.sitepoint.com/hierarchical-data-database-2/
     * ********************************************************************/
    public void applyMPTTLabel() {
        applyMPTTLabel(1);
    }

    private int applyMPTTLabel(int label) {
        mLeftMPTTMarker = label;
        if (mLeft != null){
            label = mLeft.applyMPTTLabel(label += 1);
        }
        if (mRight != null){
            label = mRight.applyMPTTLabel(label += 1);
        }
        ++label;
        mRightMPTTMarker = label;
        return label;
    }

    /* ********************************************************************
     *  Iterate over the tree in several orders
     * ********************************************************************/
    public List<ConditionsTreeNode> preorder() {
        ArrayList<ConditionsTreeNode> result = new ArrayList<ConditionsTreeNode>();
        Stack<ConditionsTreeNode> stack = new Stack<ConditionsTreeNode>();
        stack.push(this);

        while(!stack.isEmpty()) {
            ConditionsTreeNode current = stack.pop( );
            if( current.mLeft != null ) stack.push( current.mLeft );
            if( current.mRight != null ) stack.push( current.mRight );
            result.add(current);
        }

        return result;
    }
}

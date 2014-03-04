package com.bitsworking.networkscanner.app;

import java.util.ArrayList;

/**
 * Created by chris on 04/03/14.
 */
public class MemberManager {
    private ArrayList<MemberDescriptor> members = new ArrayList<MemberDescriptor>();

    public void addMember(MemberDescriptor md) {
        members.add(md);
    }
}

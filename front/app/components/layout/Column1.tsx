import React from "react";

interface NavigationContent extends React.PropsWithChildren {}

const Column1: React.FC<NavigationContent> = ({children}) => {
     return <div
     className="w-full md:flex-1 flex flex-col px-4 md:px-1 pt-3 md:pt-0 gap-4 md:max-w-180"
    >
        {children}
    </div>
}

export default Column1
